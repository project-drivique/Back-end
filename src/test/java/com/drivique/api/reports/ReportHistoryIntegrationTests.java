package com.drivique.api.reports;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.RoleRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReportHistoryIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;

    private static final String BODY = """
            {"typeCode": "RESERVATIONS", "typeName": "Reporte de Reservas", "title": "Reporte de Reservas",
             "format": "PDF", "totalRecords": 2,
             "filters": {"sucursal": "Poblado"},
             "snapshot": {"headers": ["Código", "Cliente"], "rows": [["R-1", "Ana"], ["R-2", "Luis"]], "kpis": []}}
            """;

    @BeforeEach
    void setup() {
        resetIamTables();
        jdbc.execute("DELETE FROM audit.generated_reports");
        Role branchAdmin = roles.save(new Role("BRANCH_ADMIN", "Branch Administrator", "Branch role", true));
        Role superAdmin = roles.save(new Role("SUPER_ADMIN", "Super Administrator", "Admin role", true));
        User manager = new User("Laura", "Encargada", "laura@drivique.com", "x");
        manager.setRoles(Set.of(branchAdmin));
        users.save(manager);
        User other = new User("Otro", "Encargado", "otro@drivique.com", "x");
        other.setRoles(Set.of(branchAdmin));
        users.save(other);
        User admin = new User("Admin", "General", "admin@drivique.com", "x");
        admin.setRoles(Set.of(superAdmin));
        users.save(admin);
    }

    @Test
    @WithMockUser(username = "laura@drivique.com", roles = "BRANCH_ADMIN")
    void managerSavesListsDownloadsAndDeletesOwnReport() throws Exception {
        mvc.perform(post("/v1/admin/reports/history").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("REP-")))
                .andExpect(jsonPath("$.totalRecords").value(2))
                .andExpect(jsonPath("$.generatedByEmail").value("laura@drivique.com"));

        mvc.perform(get("/v1/admin/reports/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].snapshot").doesNotExist());

        String id = jdbc.queryForObject("SELECT id::text FROM audit.generated_reports", String.class);
        mvc.perform(get("/v1/admin/reports/history/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.rows.length()").value(2));

        mvc.perform(delete("/v1/admin/reports/history/" + id)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.generated_reports", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit.audit_logs WHERE domain_name = 'REPORTS'", Integer.class)).isGreaterThanOrEqualTo(3);
    }

    @Test
    @WithMockUser(username = "otro@drivique.com", roles = "BRANCH_ADMIN")
    void managerCannotSeeAnotherUsersReport() throws Exception {
        jdbc.execute("INSERT INTO audit.administrative_report_types (id, code, name, is_active, created_at, updated_at) "
                + "SELECT gen_random_uuid(), 'RESERVATIONS', 'Reservas', true, now(), now() WHERE NOT EXISTS "
                + "(SELECT 1 FROM audit.administrative_report_types WHERE code = 'RESERVATIONS')");
        String lauraId = jdbc.queryForObject("SELECT id::text FROM iam.users WHERE email = 'laura@drivique.com'", String.class);
        jdbc.execute("INSERT INTO audit.generated_reports (id, report_type_id, format, generated_by, filters, status, generated_at, created_at, updated_at, "
                + "report_code, title, total_records, snapshot) SELECT gen_random_uuid(), t.id, 'PDF', '" + lauraId + "', '{}', 'COMPLETED', now(), now(), now(), "
                + "'REP-X', 'De Laura', 1, '{\"rows\": []}'::jsonb FROM audit.administrative_report_types t WHERE t.code = 'RESERVATIONS'");
        String id = jdbc.queryForObject("SELECT id::text FROM audit.generated_reports", String.class);

        mvc.perform(get("/v1/admin/reports/history")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/v1/admin/reports/history/" + id)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@drivique.com", roles = "SUPER_ADMIN")
    void superAdminSeesEveryReportAndCanClearHistory() throws Exception {
        mvc.perform(post("/v1/admin/reports/history").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        mvc.perform(get("/v1/admin/reports/history")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/v1/admin/reports/history")).andExpect(status().isNoContent());
        mvc.perform(get("/v1/admin/reports/history")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotUseReports() throws Exception {
        mvc.perform(get("/v1/admin/reports/history")).andExpect(status().isForbidden());
    }
}
