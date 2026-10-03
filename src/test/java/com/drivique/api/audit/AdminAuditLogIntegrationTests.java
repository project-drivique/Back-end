package com.drivique.api.audit;

import com.drivique.api.DatabaseHealthTestSupport;
import com.drivique.api.model.AuditLog;
import com.drivique.api.model.Branch;
import com.drivique.api.model.City;
import com.drivique.api.model.Department;
import com.drivique.api.model.User;
import com.drivique.api.repository.AuditLogRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.CityRepository;
import com.drivique.api.repository.DepartmentRepository;
import com.drivique.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
class AdminAuditLogIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private User adminUser;
    private Branch branch;
    private AuditLog log1;

    @BeforeEach
    void setup() {
        auditLogRepository.deleteAll();
        resetIamTables();
        resetLocationTables();

        Department dep = departmentRepository.saveAndFlush(new Department("Antioquia"));
        City city = cityRepository.saveAndFlush(new City(dep, "Medellín", true, true));
        branch = branchRepository.saveAndFlush(new Branch("Sede Poblado", "Cra 43A # 1-50", city, "3000000000", LocalTime.of(8, 0), LocalTime.of(18, 0), true));

        adminUser = userRepository.saveAndFlush(new User("Admin", "Auditor", "auditor.admin@drivique.com", "secretpass"));

        log1 = auditLogRepository.saveAndFlush(new AuditLog(
                "FLEET",
                "Vehicle",
                UUID.randomUUID(),
                "UPDATE",
                "SUCCESS",
                adminUser,
                branch,
                "190.24.5.10",
                "Actualización de vehículo",
                "{\"mileage\":10000}",
                "{\"mileage\":12500}"
        ));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "auditor.admin@drivique.com")
    void getAuditLogs_AsAdmin_ReturnsList() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].domainName").value("FLEET"))
                .andExpect(jsonPath("$[0].entityName").value("Vehicle"))
                .andExpect(jsonPath("$[0].actorEmail").value("auditor.admin@drivique.com"))
                .andExpect(jsonPath("$[0].ipAddress").value("190.24.5.10"));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "auditor.admin@drivique.com")
    void getAuditLogs_WithEntityFilter_ReturnsFilteredList() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs")
                        .contextPath("/api")
                        .param("entityName", "Vehicle")
                        .param("domain", "FLEET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(get("/api/v1/admin/audit-logs")
                        .contextPath("/api")
                        .param("entityName", "NonExisting"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "auditor.admin@drivique.com")
    void getAuditLogById_AsAdmin_ReturnsDetail() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs/{id}", log1.getId()).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(log1.getId().toString()))
                .andExpect(jsonPath("$.domainName").value("FLEET"))
                .andExpect(jsonPath("$.oldData").value("{\"mileage\":10000}"))
                .andExpect(jsonPath("$.newData").value("{\"mileage\":12500}"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonAdminUser_AccessingAuditLogs_ReturnsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs").contextPath("/api"))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/admin/audit-logs/{id}", log1.getId()).contextPath("/api"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccess_ReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/admin/audit-logs").contextPath("/api"))
                .andExpect(status().isUnauthorized());
    }
}
