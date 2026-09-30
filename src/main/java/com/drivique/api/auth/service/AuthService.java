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

    public AuthService(
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
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
}
