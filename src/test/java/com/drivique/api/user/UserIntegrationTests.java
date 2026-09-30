package com.drivique.api.user;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.auth.entity.Role;
import com.drivique.api.auth.entity.User;
import com.drivique.api.auth.repository.RoleRepository;
import com.drivique.api.auth.repository.UserRepository;
import com.drivique.api.auth.service.JwtService;
import com.drivique.api.user.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class UserIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserPreferenceRepository preferenceRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        resetIamTables();

        Role customerRole = roleRepository.save(new Role(
                UUID.randomUUID(),
                "CUSTOMER",
                "Customer",
                "Customer role",
                true
        ));

        testUser = new User("Ana", "Martinez", "ana.martinez@drivique.com", passwordEncoder.encode("SecurePass123!"));
        testUser.setPhone("+573009876543");
        testUser.setDocumentNumber("1122334455");
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);

        jwtToken = jwtService.generateAccessToken(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getFullName(),
                List.of("CUSTOMER")
        );
    }

    @Test
    void getMyProfileWithoutAuthReturns401() throws Exception {
        mvc.perform(get("/api/v1/users/me").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyProfileWithAuthReturnsUserData() throws Exception {
        mvc.perform(get("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana.martinez@drivique.com"))
                .andExpect(jsonPath("$.firstName").value("Ana"))
                .andExpect(jsonPath("$.lastName").value("Martinez"))
                .andExpect(jsonPath("$.phone").value("+573009876543"));
    }

    @Test
    void updateMyProfileUpdatesNonCriticalFields() throws Exception {
        String updateBody = """
                {
                    "firstName": "Ana Maria",
                    "lastName": "Martinez Rodriguez",
                    "phone": "+573123456789",
                    "birthDate": "1992-04-10",
                    "nationalityId": null
                }
                """;

        mvc.perform(put("/api/v1/users/me")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ana Maria"))
                .andExpect(jsonPath("$.lastName").value("Martinez Rodriguez"))
                .andExpect(jsonPath("$.phone").value("+573123456789"))
                .andExpect(jsonPath("$.birthDate").value("1992-04-10"));

        User updatedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("Ana Maria");
        assertThat(updatedUser.getEmail()).isEqualTo("ana.martinez@drivique.com"); // Remains untouched
    }

    @Test
    void getMyPreferencesReturnsDefaultsInitially() throws Exception {
        mvc.perform(get("/api/v1/users/me/preferences")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreference").value("SYSTEM"))
                .andExpect(jsonPath("$.emailNotifications").value(true))
                .andExpect(jsonPath("$.smsNotifications").value(true));
    }

    @Test
    void updateMyPreferencesUpdatesConfig() throws Exception {
        String updateBody = """
                {
                    "themePreference": "DARK",
                    "emailNotifications": false,
                    "smsNotifications": true
                }
                """;

        mvc.perform(put("/api/v1/users/me/preferences")
                        .contextPath("/api")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themePreference").value("DARK"))
                .andExpect(jsonPath("$.emailNotifications").value(false))
                .andExpect(jsonPath("$.smsNotifications").value(true));
    }
}
