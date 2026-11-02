package com.drivique.api.auth;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AuthIntegrationTests extends AuthIntegrationTestBase {

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
    void changePasswordWorksAndTheOldOneStopsWorking() throws Exception {
        changePassword("SecureP@ssw0rd123", "NuevaClave#2026x")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Inicia sesión de nuevo")));

        assertThat(loginStatus("SecureP@ssw0rd123")).isEqualTo(401);
        assertThat(loginStatus("NuevaClave#2026x")).isEqualTo(200);
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIsRefusedAndCounted() throws Exception {
        changePassword("claveEquivocada1!", "NuevaClave#2026x")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.meta.remainingAttempts").value(4));

        assertThat(userRepository.findById(testUser.getId()).orElseThrow().getFailedLoginAttempts()).isEqualTo((short) 1);
        assertThat(loginStatus("SecureP@ssw0rd123")).isEqualTo(200);
    }

    @Test
    void changePasswordRejectsWeakAndRepeatedPasswords() throws Exception {
        changePassword("SecureP@ssw0rd123", "corta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("WEAK_PASSWORD"));
        changePassword("SecureP@ssw0rd123", "SecureP@ssw0rd123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("SAME_PASSWORD"));
    }

    @Test
    void changePasswordRequiresASession() throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyPasswordAnswersNoContentOrAClearError() throws Exception {
        var user = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("juan.perez@drivique.com").roles("CUSTOMER");
        mvc.perform(post("/api/v1/auth/verify-password").contextPath("/api").with(user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"SecureP@ssw0rd123\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/verify-password").contextPath("/api").with(user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"mala\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"));
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
                .andExpect(status().isUnauthorized())
                // El mensaje dice qué falló y cuántos intentos quedan, con un código estable para web y app
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_PASSWORD"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.meta.remainingAttempts").value(4))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Contraseña incorrecta")));

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
}
