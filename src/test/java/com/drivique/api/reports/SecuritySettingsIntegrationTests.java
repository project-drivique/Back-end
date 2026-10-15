package com.drivique.api.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class SecuritySettingsIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    @BeforeEach
    void setUp() {
        resetIamTables();
        jdbc.execute("DELETE FROM iam.security_configurations");
        jdbc.execute("INSERT INTO iam.security_configurations (id, config_key, config_value, description) VALUES "
                + "(gen_random_uuid(), 'MAX_LOGIN_ATTEMPTS', '5', 'Intentos'), "
                + "(gen_random_uuid(), 'MFA_REQUIRED_FOR_ADMINS', 'true', 'MFA'), "
                + "(gen_random_uuid(), 'SOME_SECRET_KEY', 'x', 'No editable')");
        Role superAdmin = roles.save(new Role("SUPER_ADMIN", "Super Administrator", "Admin role", true));
        Role branchAdmin = roles.save(new Role("BRANCH_ADMIN", "Branch Administrator", "Branch role", true));
        User admin = new User("Admin", "General", "admin@drivique.com", "x");
        admin.setRoles(Set.of(superAdmin));
        users.save(admin);
        User manager = new User("Laura", "Encargada", "laura@drivique.com", "x");
        manager.setRoles(Set.of(branchAdmin));
        users.save(manager);
    }

    private String change(String key, String value) {
        return "{\"value\":\"" + value + "\",\"reason\":\"prueba\"}";
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void settingsAreValidatedAuditedAndCanBeReverted() throws Exception {
        mvc.perform(get("/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(put("/v1/admin/settings/MAX_LOGIN_ATTEMPTS").contentType(MediaType.APPLICATION_JSON).content(change("x", "99")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/settings/MAX_LOGIN_ATTEMPTS").contentType(MediaType.APPLICATION_JSON).content(change("x", "abc")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/settings/MFA_REQUIRED_FOR_ADMINS").contentType(MediaType.APPLICATION_JSON).content(change("x", "quizas")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/v1/admin/settings/SOME_SECRET_KEY").contentType(MediaType.APPLICATION_JSON).content(change("x", "1")))
                .andExpect(status().isNotFound());

        mvc.perform(put("/v1/admin/settings/MAX_LOGIN_ATTEMPTS").contentType(MediaType.APPLICATION_JSON).content(change("x", "7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("7"))
                .andExpect(jsonPath("$.canRevert").value(true))
                .andExpect(jsonPath("$.previousValue").value("5"));
        assertThat(jdbc.queryForObject("SELECT config_value FROM iam.security_configurations WHERE config_key = 'MAX_LOGIN_ATTEMPTS'", String.class)).isEqualTo("7");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_logs WHERE domain_name = 'CONFIGURATION' AND result = 'SUCCESS'", Integer.class)).isEqualTo(1);

        mvc.perform(put("/v1/admin/settings/MAX_LOGIN_ATTEMPTS").contentType(MediaType.APPLICATION_JSON).content(change("x", "7")))
                .andExpect(status().isConflict());

        mvc.perform(post("/v1/admin/settings/MAX_LOGIN_ATTEMPTS/revert"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("5"))
                .andExpect(jsonPath("$.canRevert").value(false));
        mvc.perform(post("/v1/admin/settings/MAX_LOGIN_ATTEMPTS/revert")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iam.security_configuration_changes WHERE config_key = 'MAX_LOGIN_ATTEMPTS'", Integer.class)).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "laura@drivique.com", roles = "BRANCH_ADMIN")
    void onlyTheGlobalAdministratorCanReadOrChangeSettings() throws Exception {
        mvc.perform(get("/v1/admin/settings")).andExpect(status().isForbidden());
        mvc.perform(put("/v1/admin/settings/MAX_LOGIN_ATTEMPTS").contentType(MediaType.APPLICATION_JSON).content(change("x", "6")))
                .andExpect(status().isForbidden());
    }
}
