package com.drivique.api.auth;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.repository.UserSocialAccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SocialAuthIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserSocialAccountRepository socialAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User existingUser;
    private Role customerRole;

    @BeforeEach
    void setUp() {
        resetIamTables();

        customerRole = roleRepository.save(new Role("CUSTOMER", "Cliente", "Rol de cliente", true));
        roleRepository.save(new Role("SUPER_ADMIN", "Super Administrador", "Rol super admin", true));

        existingUser = new User("Ana", "Gomez", "ana.gomez@drivique.com", passwordEncoder.encode("SecureP@ssw0rd123"));
        existingUser.setRoles(Set.of(customerRole));
        existingUser = userRepository.save(existingUser);
    }

    @Test
    void socialLoginGoogleNewUserSuccess() throws Exception {
        String payload = """
                {
                    "provider": "GOOGLE",
                    "idToken": "sandbox_google_token:new.google.user@drivique.com",
                    "deviceInfo": "Chrome 128 on Windows"
                }
                """;

        mvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.userProfile.email").value("new.google.user@drivique.com"))
                .andExpect(jsonPath("$.userProfile.roles[0]").value("CUSTOMER"));

        var user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("new.google.user@drivique.com");
        assertThat(user).isPresent();
        assertThat(user.get().getEmailVerifiedAt()).isNotNull();
        assertThat(socialAccountRepository.findAllByUserId(user.get().getId())).hasSize(1);
    }

    @Test
    void socialLoginFacebookAutoLinksExistingUser() throws Exception {
        String payload = """
                {
                    "provider": "FACEBOOK",
                    "accessToken": "sandbox_facebook_token:ana.gomez@drivique.com",
                    "deviceInfo": "Safari on iOS"
                }
                """;

        mvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userProfile.email").value("ana.gomez@drivique.com"))
                .andExpect(jsonPath("$.userProfile.firstName").value("Ana"));

        var socialAccounts = socialAccountRepository.findAllByUserId(existingUser.getId());
        assertThat(socialAccounts).hasSize(1);
        assertThat(socialAccounts.get(0).getProvider()).isEqualTo("FACEBOOK");
    }

    @Test
    void shortcutEndpointsGoogleAndFacebookWork() throws Exception {
        // Test /v1/auth/google
        mvc.perform(post("/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"sandbox_google_token:direct.google@drivique.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userProfile.email").value("direct.google@drivique.com"));

        // Test /v1/auth/facebook
        mvc.perform(post("/v1/auth/facebook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"sandbox_fb_token:direct.facebook@drivique.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userProfile.email").value("direct.facebook@drivique.com"));
    }

    @Test
    void invalidTokenReturns401Unauthorized() throws Exception {
        String payload = """
                {
                    "provider": "GOOGLE",
                    "idToken": "invalid.jwt.token.format"
                }
                """;

        mvc.perform(post("/v1/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void linkAndUnlinkAccountFlow() throws Exception {
        // 1. Log in with password to get JWT
        String loginPayload = """
                {
                    "email": "ana.gomez@drivique.com",
                    "password": "SecureP@ssw0rd123"
                }
                """;
        MvcResult loginResult = mvc.perform(post("/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn();

        String jwt = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        // 2. Link Google account
        String linkPayload = """
                {
                    "provider": "GOOGLE",
                    "idToken": "sandbox_google_token:ana.gomez@drivique.com"
                }
                """;
        mvc.perform(post("/v1/auth/social/link")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"));

        // 3. List linked accounts
        mvc.perform(get("/v1/auth/social/accounts")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].provider").value("GOOGLE"));

        // 4. Unlink Google account
        mvc.perform(delete("/v1/auth/social/GOOGLE")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());

        // 5. Verify unlinked
        mvc.perform(get("/v1/auth/social/accounts")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
