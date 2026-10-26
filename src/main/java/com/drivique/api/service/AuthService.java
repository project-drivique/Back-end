package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.model.Permission;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.model.UserSession;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.UserSessionRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.AccountLockedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordValidatorService passwordValidatorService;
    private final RoleRepository roleRepository;
    private final AuthEmailService authEmailService;
    private final BranchUserRepository branchUserRepository;
    private final NotificationService notificationService;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            VerificationCodeService verificationCodeService,
            PasswordValidatorService passwordValidatorService,
            RoleRepository roleRepository,
            AuthEmailService authEmailService,
            BranchUserRepository branchUserRepository,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationCodeService = verificationCodeService;
        this.passwordValidatorService = passwordValidatorService;
        this.roleRepository = roleRepository;
        this.authEmailService = authEmailService;
        this.branchUserRepository = branchUserRepository;
        this.notificationService = notificationService;
    }

    public AuthService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            VerificationCodeService verificationCodeService,
            PasswordValidatorService passwordValidatorService,
            RoleRepository roleRepository,
            AuthEmailService authEmailService,
            BranchUserRepository branchUserRepository
    ) {
        this(userRepository, sessionRepository, passwordEncoder, jwtService, verificationCodeService,
                passwordValidatorService, roleRepository, authEmailService, branchUserRepository, null);
    }

    @Transactional
    public MessageResponseDTO register(RegisterRequestDTO request) {
        String email = request.email().trim().toLowerCase();
        var existing = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (existing.isPresent()) {
            User existingUser = existing.get();
            if ("PENDING_VERIFICATION".equalsIgnoreCase(existingUser.getAccountStatus())) {
                passwordValidatorService.validate(request.password());
                existingUser.setPasswordHash(passwordEncoder.encode(request.password()));
                if (request.firstName() != null && !request.firstName().isBlank()) {
                    existingUser.setFirstName(request.firstName().trim());
                }
                if (request.lastName() != null && !request.lastName().isBlank()) {
                    existingUser.setLastName(request.lastName().trim());
                }
                userRepository.save(existingUser);
                String code = verificationCodeService.createVerificationCode(existingUser, "ACCOUNT_VERIFICATION", 15);
                authEmailService.sendOtp(email, "verificar tu cuenta", code);
                return MessageResponseDTO.of("Si el correo es válido, se envió un código de verificación.");
            }
            throw new ConflictException("El correo ya se encuentra registrado. Inicia sesión o recupera tu contraseña.");
        }
        passwordValidatorService.validate(request.password());
        User user = new User(request.firstName().trim(), request.lastName().trim(), email, passwordEncoder.encode(request.password()));
        user.setAccountStatus("PENDING_VERIFICATION");
        user.setRoles(java.util.Set.of(roleRepository.findByCodeAndActiveTrue("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Rol CUSTOMER no configurado."))));
        userRepository.save(user);
        String code = verificationCodeService.createVerificationCode(user, "ACCOUNT_VERIFICATION", 15);
        authEmailService.sendOtp(email, "verificar tu cuenta", code);
        return MessageResponseDTO.of("Si el correo es válido, se envió un código de verificación.");
    }

    @Transactional
    public MessageResponseDTO resendVerificationCode(ForgotPasswordRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).orElseThrow(() -> accountNotFound());
        String code = verificationCodeService.createVerificationCode(user, "ACCOUNT_VERIFICATION", 15);
        authEmailService.sendOtp(user.getEmail(), "verificar tu cuenta", code);
        return MessageResponseDTO.of("Si el correo es válido, se envió un código de verificación.");
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AuditService auditService;

    /** Los accesos (exitosos o no) quedan en la auditoría; si el registro falla, el inicio de sesión no se afecta. */
    private void auditAccess(String email, String result, String description, String ipAddress) {
        if (auditService == null) {
            return;
        }
        try {
            auditService.recordAudit("AUTH", "Session", null, "INSERT", result, email, null, ipAddress, description, null, null);
        } catch (Exception ignored) {
            // la auditoría nunca bloquea el acceso
        }
    }

    /** Correo sin cuenta: se avisa con un mensaje claro y nunca se envía un código. */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;

    private static com.drivique.api.exception.LoginFailedException accountNotFound() {
        return new com.drivique.api.exception.LoginFailedException(org.springframework.http.HttpStatus.NOT_FOUND,
                "ACCOUNT_NOT_FOUND", "email", "No existe una cuenta registrada con ese correo.", null);
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class, com.drivique.api.exception.LoginFailedException.class})
    public AuthResponseDTO login(LoginRequestDTO request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> accountNotFound());

        if (user.isLocked()) {
            auditAccess(user.getEmail(), "FAILURE", "Intento de inicio de sesión con la cuenta bloqueada", ipAddress);
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.incrementFailedAttempts(MAX_FAILED_LOGIN_ATTEMPTS, 30);
            userRepository.save(user);

            if (user.isLocked()) {
                auditAccess(user.getEmail(), "FAILURE", "Cuenta bloqueada por intentos fallidos de inicio de sesión", ipAddress);
                throw new AccountLockedException(user.getLockedUntil());
            }
            auditAccess(user.getEmail(), "FAILURE", "Inicio de sesión fallido: contraseña incorrecta", ipAddress);
            int remaining = Math.max(0, MAX_FAILED_LOGIN_ATTEMPTS - user.getFailedLoginAttempts());
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "password",
                    "Contraseña incorrecta. Intentos restantes antes de bloquear la cuenta: " + remaining + ".",
                    java.util.Map.of("remainingAttempts", remaining));
        }

        user.resetFailedAttempts();
        userRepository.save(user);

        if ("PENDING_VERIFICATION".equalsIgnoreCase(user.getAccountStatus())) {
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "email",
                    "Debes verificar tu correo electrónico antes de iniciar sesión.", null);
        }

        if ("INACTIVE".equalsIgnoreCase(user.getAccountStatus()) || "SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
            auditAccess(user.getEmail(), "FAILURE", "Inicio de sesión rechazado: la cuenta no está activa", ipAddress);
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "email",
                    "Tu cuenta está desactivada. Contacta a soporte.", null);
        }

        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .filter(Objects::nonNull)
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        UUID branchId = null;
        String branchName = null;
        var branchAssignment = branchUserRepository.findByUserId(user.getId());
        if (branchAssignment.isPresent()) {
            branchId = branchAssignment.get().getBranchId();
            if (branchAssignment.get().getBranch() != null) {
                branchName = branchAssignment.get().getBranch().getName();
            }
        }

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), roleCodes, permissions, branchId, branchName);
        String refreshToken = jwtService.generateRefreshToken();
        String refreshTokenHash = jwtService.hashToken(refreshToken);

        Instant sessionExpiresAt = Instant.now().plus(jwtService.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);
        UserSession session = new UserSession(
                user,
                refreshTokenHash,
                request.deviceInfo(),
                ipAddress,
                userAgent,
                sessionExpiresAt
        );
        sessionRepository.save(session);

        UserProfileResponseDTO profile = new UserProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                roleCodes,
                user.isProfileComplete(),
                user.getAccountStatus(),
                branchId,
                branchName,
                permissions
        );

        auditAccess(user.getEmail(), "SUCCESS", "Inicio de sesión exitoso", ipAddress);
        return AuthResponseDTO.of(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds(), profile);
    }

    @Transactional
    public AuthResponseDTO refresh(RefreshTokenRequestDTO request, String ipAddress, String userAgent) {
        String tokenHash = jwtService.hashToken(request.refreshToken());
        UserSession session = sessionRepository.findByRefreshTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado."));

        if (!session.isValid()) {
            throw new BadCredentialsException("Refresh token inválido o expirado.");
        }

        User user = session.getUser();
        if (user.isLocked() || "INACTIVE".equalsIgnoreCase(user.getAccountStatus()) || "SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
            session.revoke();
            sessionRepository.save(session);
            throw new BadCredentialsException("La cuenta no está autorizada para renovar la sesión.");
        }

        // Token rotation: revoke old session and issue new tokens
        session.revoke();
        sessionRepository.save(session);

        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .filter(Objects::nonNull)
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        UUID branchId = null;
        String branchName = null;
        var branchAssignment = branchUserRepository.findByUserId(user.getId());
        if (branchAssignment.isPresent()) {
            branchId = branchAssignment.get().getBranchId();
            if (branchAssignment.get().getBranch() != null) {
                branchName = branchAssignment.get().getBranch().getName();
            }
        }

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), roleCodes, permissions, branchId, branchName);
        String newRefreshToken = jwtService.generateRefreshToken();
        String newRefreshTokenHash = jwtService.hashToken(newRefreshToken);

        Instant sessionExpiresAt = Instant.now().plus(jwtService.getRefreshTokenExpirationDays(), ChronoUnit.DAYS);
        UserSession newSession = new UserSession(
                user,
                newRefreshTokenHash,
                session.getDeviceInfo(),
                ipAddress,
                userAgent,
                sessionExpiresAt
        );
        sessionRepository.save(newSession);

        UserProfileResponseDTO profile = new UserProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                roleCodes,
                user.isProfileComplete(),
                user.getAccountStatus(),
                branchId,
                branchName,
                permissions
        );

        return AuthResponseDTO.of(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpirationSeconds(), profile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDTO getCurrentSession(String email) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED, "Sesión no encontrada o usuario inexistente."));

        if (user.isLocked() || "INACTIVE".equalsIgnoreCase(user.getAccountStatus()) || "SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "La cuenta está inactiva o suspendida.");
        }

        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).toList();
        List<String> permissions = user.getRoles().stream()
                .filter(Objects::nonNull)
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();

        UUID branchId = null;
        String branchName = null;
        var branchAssignment = branchUserRepository.findByUserId(user.getId());
        if (branchAssignment.isPresent()) {
            branchId = branchAssignment.get().getBranchId();
            if (branchAssignment.get().getBranch() != null) {
                branchName = branchAssignment.get().getBranch().getName();
            }
        }

        return new UserProfileResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                roleCodes,
                user.isProfileComplete(),
                user.getAccountStatus(),
                branchId,
                branchName,
                permissions
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = jwtService.hashToken(refreshToken);
            sessionRepository.findByRefreshTokenHash(tokenHash).ifPresent(session -> {
                session.revoke();
                sessionRepository.save(session);
            });
        }
    }

    @Transactional
    public MessageResponseDTO verifyEmail(VerifyEmailRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Usuario no encontrado."));

        verificationCodeService.validateAndConsume(user, "ACCOUNT_VERIFICATION", request.code());
        user.setEmailVerifiedAt(Instant.now());
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);

        if (notificationService != null) {
            try {
                notificationService.emitDomainEvent(
                        user,
                        "REGISTRATION",
                        "HIGH",
                        user.getId(),
                        java.util.Map.of("firstName", user.getFirstName() != null ? user.getFirstName() : "Usuario"),
                        "REG-" + user.getId()
                );
            } catch (Exception e) {
                // Notificación no bloquea la verificación
            }
        }

        return MessageResponseDTO.of("Correo electrónico verificado exitosamente.");
    }

    @Transactional
    public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).orElseThrow(() -> accountNotFound());
        String code = verificationCodeService.createVerificationCode(user, "PASSWORD_RESET", 15);
        authEmailService.sendOtp(user.getEmail(), "restablecer tu contraseña", code);
        return MessageResponseDTO.of("Si el correo está registrado, se ha enviado un código de verificación.");
    }

    @Transactional(readOnly = true)
    public MessageResponseDTO validateResetCode(ValidateResetCodeRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Código de verificación inválido o expirado."));
        verificationCodeService.validate(user, "PASSWORD_RESET", request.code());
        return MessageResponseDTO.of("Código verificado.");
    }

    /**
     * Comprueba la contraseña actual de la persona autenticada (por ejemplo antes de cambiar su correo).
     * Cuenta como intento fallido igual que el inicio de sesión, para que no sirva para adivinar la clave.
     */
    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class, com.drivique.api.exception.LoginFailedException.class})
    public void verifyPassword(String email, String password) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> accountNotFound());
        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.incrementFailedAttempts(MAX_FAILED_LOGIN_ATTEMPTS, 30);
            userRepository.save(user);
            if (user.isLocked()) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            int remaining = Math.max(0, MAX_FAILED_LOGIN_ATTEMPTS - user.getFailedLoginAttempts());
            // 400 y no 401: una contraseña equivocada aquí no significa que la sesión haya vencido
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "password",
                    "La contraseña actual es incorrecta. Intentos restantes antes de bloquear la cuenta: " + remaining + ".",
                    java.util.Map.of("remainingAttempts", remaining));
        }
        user.resetFailedAttempts();
        userRepository.save(user);
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class, com.drivique.api.exception.LoginFailedException.class})
    public MessageResponseDTO changePassword(String email, ChangePasswordRequestDTO request) {
        verifyPassword(email, request.currentPassword());
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> accountNotFound());
        if (request.currentPassword().equals(request.newPassword())) {
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "newPassword",
                    "La nueva contraseña debe ser diferente a la actual.", null);
        }
        try {
            passwordValidatorService.validate(request.newPassword());
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 400) {
                throw ex;
            }
            throw new com.drivique.api.exception.LoginFailedException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "newPassword",
                    "La nueva contraseña no cumple los requisitos de seguridad.", null);
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Por seguridad se cierran todas las sesiones: hay que volver a iniciar con la nueva contraseña
        sessionRepository.revokeAllActiveSessionsForUser(user, Instant.now());
        auditAccess(user.getEmail(), "SUCCESS", "Contraseña cambiada por la propia persona", null);
        return MessageResponseDTO.of("Contraseña actualizada. Inicia sesión de nuevo con tu nueva contraseña.");
    }

    @Transactional
    public MessageResponseDTO resetPassword(ResetPasswordRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "Código de verificación inválido o expirado."));

        verificationCodeService.validateAndConsume(user, "PASSWORD_RESET", request.code());
        passwordValidatorService.validate(request.newPassword());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.resetFailedAttempts();
        userRepository.save(user);

        sessionRepository.revokeAllActiveSessionsForUser(user, Instant.now());

        return MessageResponseDTO.of("Contraseña restablecida exitosamente. Todas las sesiones activas han sido invalidadas.");
    }
}
