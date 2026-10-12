package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.AccountLockedException;
import com.drivique.api.model.Permission;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.model.UserPreference;
import com.drivique.api.model.UserSession;
import com.drivique.api.model.UserSocialAccount;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.UserSessionRepository;
import com.drivique.api.repository.UserSocialAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SocialAuthService {

    private final UserRepository userRepository;
    private final UserSocialAccountRepository socialAccountRepository;
    private final UserSessionRepository sessionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final RoleRepository roleRepository;
    private final OAuthProviderService oAuthProviderService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final BranchUserRepository branchUserRepository;

    public SocialAuthService(
            UserRepository userRepository,
            UserSocialAccountRepository socialAccountRepository,
            UserSessionRepository sessionRepository,
            UserPreferenceRepository userPreferenceRepository,
            RoleRepository roleRepository,
            OAuthProviderService oAuthProviderService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            BranchUserRepository branchUserRepository
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.sessionRepository = sessionRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.roleRepository = roleRepository;
        this.oAuthProviderService = oAuthProviderService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.branchUserRepository = branchUserRepository;
    }

    @Transactional
    public AuthResponseDTO login(SocialLoginRequestDTO request, String ipAddress, String userAgent) {
        OAuthUserInfo oauthUser = oAuthProviderService.verifyAndExtract(request);

        // 1. Check if social account is already linked
        Optional<UserSocialAccount> existingSocial = socialAccountRepository
                .findByProviderIgnoreCaseAndProviderUserId(oauthUser.provider(), oauthUser.providerUserId());

        User user;
        if (existingSocial.isPresent()) {
            user = existingSocial.get().getUser();
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(Instant.now());
                userRepository.save(user);
            }
        } else {
            // 2. Check if user exists by email
            Optional<User> existingUser = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(oauthUser.email());
            if (existingUser.isPresent()) {
                user = existingUser.get();
                if (user.getEmailVerifiedAt() == null) {
                    user.setEmailVerifiedAt(Instant.now());
                }
                userRepository.save(user);

                // Auto-link social account to existing user
                UserSocialAccount newSocial = new UserSocialAccount(
                        user,
                        oauthUser.provider().toUpperCase(),
                        oauthUser.providerUserId(),
                        oauthUser.email()
                );
                socialAccountRepository.save(newSocial);
            } else {
                // 3. Auto-provision new customer account
                user = new User(
                        oauthUser.firstName() != null && !oauthUser.firstName().isBlank() ? oauthUser.firstName() : "Usuario",
                        oauthUser.lastName() != null && !oauthUser.lastName().isBlank() ? oauthUser.lastName() : "Social",
                        oauthUser.email(),
                        passwordEncoder.encode(UUID.randomUUID().toString())
                );
                user.setEmailVerifiedAt(Instant.now());
                user.setAccountStatus("ACTIVE");
                user.setProfileComplete(false);

                // Assign default CUSTOMER role
                Role customerRole = roleRepository.findByCode("CUSTOMER")
                        .orElseThrow(() -> new IllegalStateException("El rol CUSTOMER no está configurado."));
                user.setRoles(Set.of(customerRole));

                userRepository.save(user);

                // Create initial default preferences
                UserPreference preference = new UserPreference(user);
                userPreferenceRepository.save(preference);

                // Link social account
                UserSocialAccount newSocial = new UserSocialAccount(
                        user,
                        oauthUser.provider().toUpperCase(),
                        oauthUser.providerUserId(),
                        oauthUser.email()
                );
                socialAccountRepository.save(newSocial);
            }
        }

        if (userPreferenceRepository.findByUserId(user.getId()).isEmpty()) {
            userPreferenceRepository.save(new UserPreference(user));
        }

        // Account status & lockout validation
        if (user.isLocked()) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if ("INACTIVE".equalsIgnoreCase(user.getAccountStatus()) || "SUSPENDED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new BadCredentialsException("La cuenta está desactivada o suspendida.");
        }

        user.resetFailedAttempts();
        userRepository.save(user);

        // Issue Drivique JWT and Refresh Token (Zero provider tokens returned or stored)
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
                request.deviceInfo() != null ? request.deviceInfo() : "Social OAuth Client",
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
    public SocialAccountResponseDTO linkAccount(String currentUserEmail, SocialLinkRequestDTO request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));

        SocialLoginRequestDTO loginReq = new SocialLoginRequestDTO(
                request.provider(),
                request.idToken(),
                request.accessToken(),
                request.authCode(),
                request.codeVerifier(),
                request.redirectUri(),
                request.nonce(),
                null,
                request.email(),
                request.firstName(),
                request.lastName()
        );

        OAuthUserInfo oauthUser = oAuthProviderService.verifyAndExtract(loginReq);

        // Check if provider is already linked to another user
        Optional<UserSocialAccount> existing = socialAccountRepository
                .findByProviderIgnoreCaseAndProviderUserId(oauthUser.provider(), oauthUser.providerUserId());

        if (existing.isPresent()) {
            if (existing.get().getUser().getId().equals(user.getId())) {
                return new SocialAccountResponseDTO(
                        existing.get().getId(),
                        existing.get().getProvider(),
                        existing.get().getEmail(),
                        existing.get().getCreatedAt()
                );
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta cuenta social ya está vinculada a otro usuario.");
        }

        // Check if user already linked this provider
        Optional<UserSocialAccount> userProviderAccount = socialAccountRepository
                .findByUserIdAndProviderIgnoreCase(user.getId(), oauthUser.provider());

        UserSocialAccount accountToSave;
        if (userProviderAccount.isPresent()) {
            accountToSave = userProviderAccount.get();
            accountToSave.setProviderUserId(oauthUser.providerUserId());
            accountToSave.setEmail(oauthUser.email());
            accountToSave.setUpdatedAt(Instant.now());
        } else {
            accountToSave = new UserSocialAccount(
                    user,
                    oauthUser.provider().toUpperCase(),
                    oauthUser.providerUserId(),
                    oauthUser.email()
            );
        }

        UserSocialAccount saved = socialAccountRepository.save(accountToSave);
        return new SocialAccountResponseDTO(saved.getId(), saved.getProvider(), saved.getEmail(), saved.getCreatedAt());
    }

    @Transactional
    public MessageResponseDTO unlinkAccount(String currentUserEmail, String provider) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));

        UserSocialAccount account = socialAccountRepository
                .findByUserIdAndProviderIgnoreCase(user.getId(), provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tienes vinculada una cuenta con este proveedor."));

        socialAccountRepository.delete(account);
        return MessageResponseDTO.of("Cuenta de " + provider.toUpperCase() + " desvinculada exitosamente.");
    }

    @Transactional(readOnly = true)
    public List<SocialAccountResponseDTO> getLinkedAccounts(String currentUserEmail) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(currentUserEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));

        return socialAccountRepository.findAllByUserId(user.getId()).stream()
                .map(a -> new SocialAccountResponseDTO(a.getId(), a.getProvider(), a.getEmail(), a.getCreatedAt()))
                .toList();
    }
}
