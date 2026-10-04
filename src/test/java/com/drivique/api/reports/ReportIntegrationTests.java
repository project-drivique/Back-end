package com.drivique.api.reports;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.AdministrativeReportType;
import com.drivique.api.model.GeneratedReport;
import com.drivique.api.model.User;
import com.drivique.api.repository.AdministrativeReportTypeRepository;
import com.drivique.api.repository.GeneratedReportRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class ReportIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdministrativeReportTypeRepository reportTypeRepository;

    @Autowired
    private GeneratedReportRepository generatedReportRepository;

    @Autowired
    private UserRepository userRepository;

    private User adminUser;

    @BeforeEach
    void setup() {
        generatedReportRepository.deleteAll();
        reportTypeRepository.deleteAll();
        resetIamTables();

        adminUser = userRepository.saveAndFlush(new User("Admin", "Manager", "admin@drivique.com", "hashedpassword"));
        reportTypeRepository.saveAndFlush(new AdministrativeReportType("FLEET_OCCUPANCY", "Ocupación de Flota", "Reporte de flota", true));
        reportTypeRepository.saveAndFlush(new AdministrativeReportType("REVENUE_SUMMARY", "Resumen de Ingresos", "Reporte de ingresos", true));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@drivique.com")
    void listReportTypes_AsAdmin_Success() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/types").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("FLEET_OCCUPANCY"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@drivique.com")
    void generateAndListReports_AsAdmin_Success() throws Exception {
        String payload = """
                {
                    "reportType": "FLEET_OCCUPANCY",
                    "format": "PDF"
                }
                """;

        mvc.perform(post("/api/v1/admin/reports/generate")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportTypeCode").value("FLEET_OCCUPANCY"))
                .andExpect(jsonPath("$.format").value("PDF"))
                .andExpect(jsonPath("$.fileUrl").isNotEmpty());

        assertThat(generatedReportRepository.count()).isEqualTo(1);

        mvc.perform(get("/api/v1/admin/reports").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reportTypeCode").value("FLEET_OCCUPANCY"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonAdminUser_AccessingReports_ReturnsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/reports").contextPath("/api"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/admin/reports/generate").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportType": "FLEET_OCCUPANCY",
                                    "format": "PDF"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccess_ReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/admin/reports").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
