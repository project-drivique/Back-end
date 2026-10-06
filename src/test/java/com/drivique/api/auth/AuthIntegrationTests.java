package com.drivique.api.auth;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.UserSessionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AuthIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private com.drivique.api.repository.VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private com.drivique.api.service.VerificationCodeService verificationCodeService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        resetIamTables();

        jdbc.execute("DELETE FROM iam.password_policies");
        jdbc.execute("INSERT INTO iam.password_policies (id, min_length, require_uppercase, require_number, require_symbol, is_active) VALUES ('" + UUID.randomUUID() + "', 8, true, true, true, true)");

        customerRole = roleRepository.save(new Role(
                "CUSTOMER",
                "Customer",
                "Customer role",
                true
        ));

        Role superAdminRole = roleRepository.save(new Role(
                "SUPER_ADMIN",
                "Super Admin",
                "Super Admin role",
                true
        ));

        testUser = new User("Juan", "Perez", "juan.perez@drivique.com", passwordEncoder.encode("SecureP@ssw0rd123"));
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
    }

    @Test
    void loginSuccessfulReturnsTokensAndProfile() throws Exception {
        String loginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "SecureP@ssw0rd123",
                    "deviceInfo": "Test Browser"
                }
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.userProfile.email").value("juan.perez@drivique.com"))
                .andExpect(jsonPath("$.userProfile.firstName").value("Juan"))
                .andExpect(jsonPath("$.userProfile.roles[0]").value("CUSTOMER"));
    }

    @Test
    void loginWithBadCredentialsFailsAndIncrementsAttempts() throws Exception {
        String badLoginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "WrongPassword999!"
                }
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginBody))
                .andExpect(status().isUnauthorized());

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFailedLoginAttempts()).isEqualTo((short) 1);
        assertThat(updatedUser.isLocked()).isFalse();
    }

    @Test
    void loginLockoutAfterFiveFailedAttemptsReturns423Locked() throws Exception {
        String badLoginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "WrongPassword!"
                }
                """;

        // Attempts 1 to 4 -> 401 Unauthorized
        for (int i = 1; i <= 4; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .contextPath("/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLoginBody))
                    .andExpect(status().isUnauthorized());
        }

        // 5th attempt -> 423 Locked
        mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLoginBody))
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("bloqueada")));

        // 6th attempt (even with right password) -> still 423 Locked
        String correctLoginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "SecureP@ssw0rd123"
                }
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctLoginBody))
                .andExpect(status().isLocked());
    }

    @Test
    void refreshTokenRotationAndReplayRejection() throws Exception {
        // 1. Initial Login
        String loginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "SecureP@ssw0rd123"
                }
                """;

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String initialRefreshToken = JsonPath.read(responseJson, "$.refreshToken");

        // 2. Perform Refresh
        String refreshBody = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, initialRefreshToken);

        MvcResult refreshResult = mvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // 3. Replay attack attempt with old refresh token must be rejected with 401
        mvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String loginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "SecureP@ssw0rd123"
                }
                """;

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

        // Logout
        String logoutBody = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);

        mvc.perform(post("/api/v1/auth/logout")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isNoContent());

        // Refresh with logged-out token must fail
        mvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtTokenAuthenticatesProtectedEndpoints() throws Exception {
        // Ensure active brand exists to be updated
        jdbc.update("DELETE FROM core.brand_configurations");
        jdbc.update("INSERT INTO core.brand_configurations (id,company_name,primary_color,secondary_color,accent_color,default_theme,is_active) VALUES (?, 'Original','#2563EB','#1E3A8A','#60A5FA','SYSTEM',true)", UUID.randomUUID());

        // Create an admin user
        Role adminRole = roleRepository.findByCode("SUPER_ADMIN").orElseThrow();
        User admin = new User("Admin", "User", "admin@drivique.com", passwordEncoder.encode("Admin123$Secure"));
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        // Login as admin
        String loginBody = """
                {
                    "email": "admin@drivique.com",
                    "password": "Admin123$Secure"
                }
                """;

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // Access protected endpoint with Bearer token
        String brandBody = """
                {
                    "companyName": "Drivique Updated",
                    "logoUrl": "https://example.com/logo.png",
                    "faviconUrl": null,
                    "primaryColor": "#112233",
                    "secondaryColor": "#445566",
                    "accentColor": "#778899",
                    "defaultTheme": "LIGHT"
                }
                """;

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/brand-configurations")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(brandBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Drivique Updated"));
    }

    @Test
    void verifyEmailSuccessWithValidOtp() throws Exception {
        String otp = verificationCodeService.createVerificationCode(testUser, "ACCOUNT_VERIFICATION", 15);

        String requestBody = String.format("""
                {
                    "email": "%s",
                    "code": "%s"
                }
                """, testUser.getEmail(), otp);

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Correo electrónico verificado exitosamente."));

        User verifiedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(verifiedUser.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    void verifyEmailWithInvalidOtpFails400() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "%s",
                    "code": "000000"
                }
                """, testUser.getEmail());

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgotPasswordGeneratesOtpSuccessfully() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "%s"
                }
                """, testUser.getEmail());

        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Si el correo está registrado, se ha enviado un código de verificación."));

        var codes = verificationCodeRepository.findAll();
        assertThat(codes).hasSize(1);
        assertThat(codes.get(0).getPurpose()).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void resetPasswordWithValidOtpUpdatesPasswordAndRevokesSessions() throws Exception {
        // 1. First login to create an active session
        String loginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "SecureP@ssw0rd123"
                }
                """;

        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.refreshToken");

        // 2. Generate OTP for password reset
        String otp = verificationCodeService.createVerificationCode(testUser, "PASSWORD_RESET", 15);

        // 3. Reset password
        String resetBody = String.format("""
                {
                    "email": "%s",
                    "code": "%s",
                    "newPassword": "NewSecurePassword456$"
                }
                """, testUser.getEmail(), otp);

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña restablecida exitosamente. Todas las sesiones activas han sido invalidadas."));

        // 4. Old refresh token should now be rejected as session was revoked
        String refreshBody = String.format("""
                {
                    "refreshToken": "%s"
                }
                """, refreshToken);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody))
                .andExpect(status().isUnauthorized());

        // 5. Login with new password works
        String newLoginBody = """
                {
                    "email": "juan.perez@drivique.com",
                    "password": "NewSecurePassword456$"
                }
                """;

        mvc.perform(post("/api/v1/auth/login")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newLoginBody))
                .andExpect(status().isOk());
    }
}
