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
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationCodeService = verificationCodeService;
        this.passwordValidatorService = passwordValidatorService;
        this.roleRepository = roleRepository;
        this.authEmailService = authEmailService;
        this.branchUserRepository = branchUserRepository;
    }

    @Transactional
    public MessageResponseDTO register(RegisterRequestDTO request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            throw new ConflictException("No fue posible procesar el registro.");
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
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).ifPresent(user -> {
            String code = verificationCodeService.createVerificationCode(user, "ACCOUNT_VERIFICATION", 15);
            authEmailService.sendOtp(user.getEmail(), "verificar tu cuenta", code);
        });
        return MessageResponseDTO.of("Si el correo es válido, se envió un código de verificación.");
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
    public AuthResponseDTO login(LoginRequestDTO request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas."));

        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.incrementFailedAttempts(5, 30);
            userRepository.save(user);

            if (user.isLocked()) {
                throw new AccountLockedException(user.getLockedUntil());
            }
            throw new BadCredentialsException("Credenciales inválidas.");
        }

        user.resetFailedAttempts();
        userRepository.save(user);

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

        return MessageResponseDTO.of("Correo electrónico verificado exitosamente.");
    }

    @Transactional
    public MessageResponseDTO forgotPassword(ForgotPasswordRequestDTO request) {
        userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(request.email()).ifPresent(user -> {
            String code = verificationCodeService.createVerificationCode(user, "PASSWORD_RESET", 15);
            authEmailService.sendOtp(user.getEmail(), "restablecer tu contraseña", code);
        });

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
