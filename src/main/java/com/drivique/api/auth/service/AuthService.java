package com.drivique.api.auth.service;

import com.drivique.api.auth.dto.*;
import com.drivique.api.auth.entity.Role;
import com.drivique.api.auth.entity.User;
import com.drivique.api.auth.entity.UserSession;
import com.drivique.api.auth.repository.UserRepository;
import com.drivique.api.auth.repository.UserSessionRepository;
import com.drivique.api.common.exception.AccountLockedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final VerificationCodeService verificationCodeService;
    private final PasswordValidatorService passwordValidatorService;

    public AuthService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            VerificationCodeService verificationCodeService,
            PasswordValidatorService passwordValidatorService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.verificationCodeService = verificationCodeService;
        this.passwordValidatorService = passwordValidatorService;
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
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), roleCodes);
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
                user.getAccountStatus()
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
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), roleCodes);
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
                user.getAccountStatus()
        );

        return AuthResponseDTO.of(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpirationSeconds(), profile);
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
            verificationCodeService.createVerificationCode(user, "PASSWORD_RESET", 15);
        });

        return MessageResponseDTO.of("Si el correo está registrado, se ha enviado un código de verificación.");
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
