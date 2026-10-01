package com.drivique.api.compliance;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.JwtService;
import com.drivique.api.repository.UserConsentRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "compliance.required-consents[0].consent-type=TERMS_AND_CONDITIONS",
        "compliance.required-consents[0].document-version=2026-09",
        "compliance.required-consents[1].consent-type=PRIVACY_POLICY",
        "compliance.required-consents[1].document-version=2026-09"
})
class ConsentIntegrationTests extends DatabaseHealthTestSupport {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private UserConsentRepository consents;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwords;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        resetIamTables();
        Role customer = roles.save(new Role("CUSTOMER", "Customer", "Customer role", true));
        user = users.save(new User("Ana", "Martinez", "ana.consent@drivique.com", passwords.encode("SecurePass123!")));
        user.setRoles(Set.of(customer));
        user = users.save(user);
        token = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getFullName(), List.of("CUSTOMER"));
    }

    @Test
    void unauthenticatedConsentRoutesReturn401() throws Exception {
        mvc.perform(post("/api/v1/consents").contextPath("/api").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"TERMS_AND_CONDITIONS\",\"documentVersion\":\"2026-09\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/consents").contextPath("/api")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/consents/status").contextPath("/api")).andExpect(status().isUnauthorized());
    }

    @Test
    void recordsSocketIpAndIgnoresSpoofableForwardedHeader() throws Exception {
        mvc.perform(post("/api/v1/consents").contextPath("/api")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", "198.51.100.99")
                        .with(request -> { request.setRemoteAddr("203.0.113.8"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"TERMS_AND_CONDITIONS\",\"documentVersion\":\"2026-09\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentType").value("TERMS_AND_CONDITIONS"))
                .andExpect(jsonPath("$.documentVersion").value("2026-09"))
                .andExpect(jsonPath("$.ipAddress").value("203.0.113.8"));
        assertThat(jdbc.queryForObject("select host(ip_address) from iam.user_consents", String.class))
                .isEqualTo("203.0.113.8");
    }

    @Test
    void duplicateVersionReturns409AndHistoryIsOnlyOwnConsents() throws Exception {
        String request = "{\"consentType\":\"TERMS_AND_CONDITIONS\",\"documentVersion\":\"2026-09\"}";
        mvc.perform(post("/api/v1/consents").contextPath("/api").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/consents").contextPath("/api").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/users/me/consents").contextPath("/api").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].consentType").value("TERMS_AND_CONDITIONS"));
        assertThat(consents.count()).isEqualTo(1);
    }

    @Test
    void statusTracksRequiredVersionsAfterRegistration() throws Exception {
        mvc.perform(get("/api/v1/consents/status").contextPath("/api").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requirementsConfigured").value(true))
                .andExpect(jsonPath("$.hasPendingConsents").value(true))
                .andExpect(jsonPath("$.pendingConsents.length()").value(2));
        mvc.perform(post("/api/v1/consents").contextPath("/api").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"TERMS_AND_CONDITIONS\",\"documentVersion\":\"2026-09\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/consents/status").contextPath("/api").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.hasPendingConsents").value(true))
                .andExpect(jsonPath("$.pendingConsents.length()").value(1))
                .andExpect(jsonPath("$.pendingConsents[0].consentType").value("PRIVACY_POLICY"));
    }

    @Test
    void validatesTypeAndVersionWithoutAcceptingClientSuppliedIp() throws Exception {
        mvc.perform(post("/api/v1/consents").contextPath("/api").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentType\":\"terms\",\"documentVersion\":\"2026 09\",\"ipAddress\":\"1.1.1.1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(consents.count()).isZero();
    }
}
