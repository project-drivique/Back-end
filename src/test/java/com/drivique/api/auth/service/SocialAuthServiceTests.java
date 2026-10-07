package com.drivique.api.auth.service;

import com.drivique.api.dto.AuthResponseDTO;
import com.drivique.api.dto.OAuthUserInfo;
import com.drivique.api.dto.SocialLoginRequestDTO;
import com.drivique.api.exception.AccountLockedException;
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
import com.drivique.api.service.JwtService;
import com.drivique.api.service.OAuthProviderService;
import com.drivique.api.service.SocialAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialAuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository socialAccountRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OAuthProviderService oAuthProviderService;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BranchUserRepository branchUserRepository;

    private SocialAuthService socialAuthService;

    private User testUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        socialAuthService = new SocialAuthService(
                userRepository,
                socialAccountRepository,
                sessionRepository,
                userPreferenceRepository,
                roleRepository,
                oAuthProviderService,
                jwtService,
                passwordEncoder,
                branchUserRepository
        );

        customerRole = new Role("CUSTOMER", "Cliente", "Rol de cliente", true);
        testUser = new User("Juan", "Perez", "juan.perez@drivique.com", "$2a$12$hash");
        testUser.setId(UUID.randomUUID());
        testUser.setRoles(Set.of(customerRole));
    }

    @Test
    void loginWithExistingLinkedAccount_ReturnsTokens() {
        SocialLoginRequestDTO request = new SocialLoginRequestDTO("GOOGLE", "valid-id-token", null, null, null, null, "nonce-123", "Browser");
        OAuthUserInfo oauthInfo = new OAuthUserInfo("GOOGLE", "google-sub-123", "juan.perez@drivique.com", "Juan", "Perez", "nonce-123");
        UserSocialAccount socialAccount = new UserSocialAccount(testUser, "GOOGLE", "google-sub-123", "juan.perez@drivique.com");

        when(oAuthProviderService.verifyAndExtract(request)).thenReturn(oauthInfo);
        when(socialAccountRepository.findByProviderIgnoreCaseAndProviderUserId("GOOGLE", "google-sub-123"))
                .thenReturn(Optional.of(socialAccount));
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("access-token-jwt");
        when(jwtService.generateRefreshToken()).thenReturn("refresh-token-plain");
        when(jwtService.hashToken("refresh-token-plain")).thenReturn("hashed-refresh-token");
        when(jwtService.getRefreshTokenExpirationDays()).thenReturn(30L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponseDTO response = socialAuthService.login(request, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token-jwt");
        assertThat(response.refreshToken()).isEqualTo("refresh-token-plain");
        assertThat(response.userProfile().email()).isEqualTo("juan.perez@drivique.com");

        verify(sessionRepository, times(1)).save(any(UserSession.class));
    }

    @Test
    void loginWithNewUser_AutoProvisionsAccountAndReturnsTokens() {
        SocialLoginRequestDTO request = new SocialLoginRequestDTO("GOOGLE", "new-user-id-token", null, null, null, null, null, "Mobile");
        OAuthUserInfo oauthInfo = new OAuthUserInfo("GOOGLE", "google-sub-999", "new.customer@gmail.com", "Nuevo", "Cliente", null);

        when(oAuthProviderService.verifyAndExtract(request)).thenReturn(oauthInfo);
        when(socialAccountRepository.findByProviderIgnoreCaseAndProviderUserId("GOOGLE", "google-sub-999"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new.customer@gmail.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$randomPasswordHash");
        when(roleRepository.findByCode("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(jwtService.generateAccessToken(any(), any(), any(), any(), any(), any(), any())).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken()).thenReturn("new-refresh-token");
        when(jwtService.hashToken("new-refresh-token")).thenReturn("new-hashed-token");
        when(jwtService.getRefreshTokenExpirationDays()).thenReturn(30L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponseDTO response = socialAuthService.login(request, "192.168.1.50", "Expo");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        verify(userRepository, times(2)).save(any(User.class));
        verify(userPreferenceRepository, times(1)).save(any(UserPreference.class));
        verify(socialAccountRepository, times(1)).save(any(UserSocialAccount.class));
        verify(sessionRepository, times(1)).save(any(UserSession.class));
    }

    @Test
    void loginWithLockedAccount_ThrowsAccountLockedException() {
        testUser.setLockedUntil(Instant.now().plusSeconds(600));
        SocialLoginRequestDTO request = new SocialLoginRequestDTO("GOOGLE", "token", null, null, null, null, null, "Web");
        OAuthUserInfo oauthInfo = new OAuthUserInfo("GOOGLE", "sub", "juan.perez@drivique.com", "Juan", "Perez", null);
        UserSocialAccount socialAccount = new UserSocialAccount(testUser, "GOOGLE", "sub", "juan.perez@drivique.com");

        when(oAuthProviderService.verifyAndExtract(request)).thenReturn(oauthInfo);
        when(socialAccountRepository.findByProviderIgnoreCaseAndProviderUserId("GOOGLE", "sub"))
                .thenReturn(Optional.of(socialAccount));

        assertThatThrownBy(() -> socialAuthService.login(request, "127.0.0.1", "Agent"))
                .isInstanceOf(AccountLockedException.class);
    }
}
