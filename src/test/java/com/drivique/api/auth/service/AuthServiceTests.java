package com.drivique.api.auth.service;

import com.drivique.api.service.*;

import com.drivique.api.dto.AuthResponseDTO;
import com.drivique.api.dto.ForgotPasswordRequestDTO;
import com.drivique.api.dto.LoginRequestDTO;
import com.drivique.api.dto.RefreshTokenRequestDTO;
import com.drivique.api.dto.RegisterRequestDTO;
import com.drivique.api.dto.ValidateResetCodeRequestDTO;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.model.UserSession;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserSessionRepository;
import com.drivique.api.exception.AccountLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class AuthServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository sessionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private PasswordValidatorService passwordValidatorService;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthEmailService authEmailService;

    private AuthService authService;
    private User testUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                sessionRepository,
                passwordEncoder,
                jwtService,
                verificationCodeService,
                passwordValidatorService,
                roleRepository,
                authEmailService
        );

        customerRole = new Role(UUID.randomUUID(), "CUSTOMER", "Customer", "Customer role", true);
        testUser = new User("Carlos", "Gomez", "carlos@drivique.com", "$2a$12$hashedPassword");
        testUser.setId(UUID.randomUUID());
        testUser.setRoles(Set.of(customerRole));
    }

    @Test
    void registerCreatesPendingCustomerAndSendsVerificationCode() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                " Carlos ", " Gomez ", " CARLOS@DRIVIQUE.COM ", "SecureP@ss123");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByCodeAndActiveTrue("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("SecureP@ss123")).thenReturn("encoded-password");
        when(verificationCodeService.createVerificationCode(any(User.class), eq("ACCOUNT_VERIFICATION"), eq(15)))
                .thenReturn("246810");

        var response = authService.register(request);

        assertThat(response.message()).contains("código de verificación");
        verify(passwordValidatorService).validate("SecureP@ss123");
        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("carlos@drivique.com")
                        && user.getFirstName().equals("Carlos")
                        && user.getLastName().equals("Gomez")
                        && user.getAccountStatus().equals("PENDING_VERIFICATION")
                        && user.getRoles().contains(customerRole)));
        verify(authEmailService).sendOtp("carlos@drivique.com", "verificar tu cuenta", "246810");
    }

    @Test
    void resendVerificationCodeSendsANewCodeForExistingUser() {
        ForgotPasswordRequestDTO request = new ForgotPasswordRequestDTO("carlos@drivique.com");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(verificationCodeService.createVerificationCode(testUser, "ACCOUNT_VERIFICATION", 15))
                .thenReturn("135790");

        var response = authService.resendVerificationCode(request);

        assertThat(response.message()).contains("código de verificación");
        verify(authEmailService).sendOtp("carlos@drivique.com", "verificar tu cuenta", "135790");
    }

    @Test
    void validateResetCodeChecksTheCodeWithoutConsumingIt() {
        ValidateResetCodeRequestDTO request = new ValidateResetCodeRequestDTO("carlos@drivique.com", "654321");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));

        var response = authService.validateResetCode(request);

        assertThat(response.message()).isEqualTo("Código verificado.");
        verify(verificationCodeService).validate(testUser, "PASSWORD_RESET", "654321");
        verify(verificationCodeService, never()).validateAndConsume(any(), any(), any());
    }

    @Test
    void loginSuccessfulReturnsTokensAndProfile() {
        LoginRequestDTO request = new LoginRequestDTO("carlos@drivique.com", "MyP@ssword123", "Chrome / Win11");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("MyP@ssword123", "$2a$12$hashedPassword")).thenReturn(true);
        when(jwtService.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("mock.jwt.token");
        when(jwtService.generateRefreshToken()).thenReturn("drivique_rf_abc123");
        when(jwtService.hashToken("drivique_rf_abc123")).thenReturn("hashedRefreshToken123");
        when(jwtService.getRefreshTokenExpirationDays()).thenReturn(7L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponseDTO response = authService.login(request, "127.0.0.1", "Mozilla/5.0");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("drivique_rf_abc123");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900L);
        assertThat(response.userProfile().email()).isEqualTo("carlos@drivique.com");

        verify(sessionRepository, times(1)).save(any(UserSession.class));
        verify(userRepository, times(1)).save(testUser);
        assertThat(testUser.getFailedLoginAttempts()).isZero();
    }

    @Test
    void loginFailureIncrementsFailedAttempts() {
        LoginRequestDTO request = new LoginRequestDTO("carlos@drivique.com", "WrongPassword", "Device");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "UserAgent"))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 1);
        verify(userRepository, times(1)).save(testUser);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void loginFifthFailureLocksAccountForThirtyMinutes() {
        testUser.setFailedLoginAttempts((short) 4);
        LoginRequestDTO request = new LoginRequestDTO("carlos@drivique.com", "WrongPassword", "Device");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "UserAgent"))
                .isInstanceOf(AccountLockedException.class);

        assertThat(testUser.getFailedLoginAttempts()).isEqualTo((short) 5);
        assertThat(testUser.isLocked()).isTrue();
        assertThat(testUser.getLockedUntil()).isAfter(Instant.now().plus(25, ChronoUnit.MINUTES));
        verify(userRepository, times(1)).save(testUser);
    }

    @Test
    void loginAttemptOnLockedAccountThrowsAccountLockedExceptionImmediately() {
        testUser.setLockedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        LoginRequestDTO request = new LoginRequestDTO("carlos@drivique.com", "MyP@ssword123", "Device");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1", "UserAgent"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void refreshRotationRevokesOldSessionAndIssuesNewTokens() {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("drivique_rf_old");
        String oldHash = "hash_old";
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.DAYS);

        UserSession oldSession = new UserSession(testUser, oldHash, "Browser", "127.0.0.1", "UA", expiresAt);

        when(jwtService.hashToken("drivique_rf_old")).thenReturn(oldHash);
        when(sessionRepository.findByRefreshTokenHash(oldHash)).thenReturn(Optional.of(oldSession));
        when(jwtService.generateAccessToken(any(), any(), any(), any()))
                .thenReturn("new.mock.jwt.token");
        when(jwtService.generateRefreshToken()).thenReturn("drivique_rf_new");
        when(jwtService.hashToken("drivique_rf_new")).thenReturn("hash_new");
        when(jwtService.getRefreshTokenExpirationDays()).thenReturn(7L);
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthResponseDTO response = authService.refresh(request, "127.0.0.1", "UA");

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new.mock.jwt.token");
        assertThat(response.refreshToken()).isEqualTo("drivique_rf_new");
        assertThat(oldSession.getRevokedAt()).isNotNull();

        verify(sessionRepository, times(2)).save(any(UserSession.class));
    }

    @Test
    void refreshWithRevokedSessionThrowsBadCredentials() {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("drivique_rf_revoked");
        String hash = "hash_revoked";
        UserSession revokedSession = new UserSession(testUser, hash, "Browser", "127.0.0.1", "UA", Instant.now().plus(5, ChronoUnit.DAYS));
        revokedSession.revoke();

        when(jwtService.hashToken("drivique_rf_revoked")).thenReturn(hash);
        when(sessionRepository.findByRefreshTokenHash(hash)).thenReturn(Optional.of(revokedSession));

        assertThatThrownBy(() -> authService.refresh(request, "127.0.0.1", "UA"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void logoutRevokesSession() {
        String refreshToken = "drivique_rf_to_logout";
        String hash = "hash_logout";
        UserSession session = new UserSession(testUser, hash, "Browser", "127.0.0.1", "UA", Instant.now().plus(5, ChronoUnit.DAYS));

        when(jwtService.hashToken(refreshToken)).thenReturn(hash);
        when(sessionRepository.findByRefreshTokenHash(hash)).thenReturn(Optional.of(session));

        authService.logout(refreshToken);

        assertThat(session.getRevokedAt()).isNotNull();
        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    void verifyEmailSuccessActivatesUser() {
        com.drivique.api.dto.VerifyEmailRequestDTO request =
                new com.drivique.api.dto.VerifyEmailRequestDTO("carlos@drivique.com", "123456");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        doNothing().when(verificationCodeService).validateAndConsume(testUser, "ACCOUNT_VERIFICATION", "123456");

        var response = authService.verifyEmail(request);

        assertThat(response.message()).contains("exitosamente");
        assertThat(testUser.getEmailVerifiedAt()).isNotNull();
        assertThat(testUser.getAccountStatus()).isEqualTo("ACTIVE");
        verify(userRepository, times(1)).save(testUser);
        verify(verificationCodeService, times(1)).validateAndConsume(testUser, "ACCOUNT_VERIFICATION", "123456");
    }

    @Test
    void forgotPasswordGeneratesOtpIfUserExists() {
        com.drivique.api.dto.ForgotPasswordRequestDTO request =
                new com.drivique.api.dto.ForgotPasswordRequestDTO("carlos@drivique.com");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));

        var response = authService.forgotPassword(request);

        assertThat(response.message()).contains("se ha enviado un código");
        verify(verificationCodeService, times(1)).createVerificationCode(testUser, "PASSWORD_RESET", 15);
    }

    @Test
    void forgotPasswordSafeWhenUserDoesNotExist() {
        com.drivique.api.dto.ForgotPasswordRequestDTO request =
                new com.drivique.api.dto.ForgotPasswordRequestDTO("unknown@drivique.com");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@drivique.com"))
                .thenReturn(Optional.empty());

        var response = authService.forgotPassword(request);

        assertThat(response.message()).contains("se ha enviado un código");
        verify(verificationCodeService, never()).createVerificationCode(any(), any(), anyInt());
    }

    @Test
    void resetPasswordSuccessUpdatesPasswordAndRevokesAllSessions() {
        com.drivique.api.dto.ResetPasswordRequestDTO request =
                new com.drivique.api.dto.ResetPasswordRequestDTO("carlos@drivique.com", "654321", "NewSecureP@ss123");

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        doNothing().when(verificationCodeService).validateAndConsume(testUser, "PASSWORD_RESET", "654321");
        doNothing().when(passwordValidatorService).validate("NewSecureP@ss123");
        when(passwordEncoder.encode("NewSecureP@ss123")).thenReturn("$2a$12$newHashedPassword");

        var response = authService.resetPassword(request);

        assertThat(response.message()).contains("restablecida exitosamente");
        assertThat(testUser.getPasswordHash()).isEqualTo("$2a$12$newHashedPassword");
        assertThat(testUser.getFailedLoginAttempts()).isZero();
        verify(userRepository, times(1)).save(testUser);
        verify(sessionRepository, times(1)).revokeAllActiveSessionsForUser(eq(testUser), any(Instant.class));
    }
}
