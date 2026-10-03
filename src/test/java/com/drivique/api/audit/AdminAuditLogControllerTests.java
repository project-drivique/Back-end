package com.drivique.api.audit;

import com.drivique.api.controller.AdminAuditLogController;
import com.drivique.api.dto.AuditLogResponseDTO;
import com.drivique.api.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminAuditLogControllerTests {

    private MockMvc mvc;

    @Mock
    private AuditService auditService;

    private AdminAuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAuditLogController(auditService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getAuditLogs_WithoutFilters_ReturnsList() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogResponseDTO dto = new AuditLogResponseDTO(
                id, "FLEET", "Vehicle", UUID.randomUUID(), "UPDATE", "SUCCESS",
                UUID.randomUUID(), "admin@drivique.com", "Admin User",
                UUID.randomUUID(), "Sede Central", "190.24.5.10",
                "Actualización de odómetro", "{\"mileage\":1000}", "{\"mileage\":1500}",
                Instant.now()
        );

        when(auditService.getAuditLogs(null, null, null, null, null, null))
                .thenReturn(List.of(dto));

        mvc.perform(get("/v1/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].domainName").value("FLEET"))
                .andExpect(jsonPath("$[0].entityName").value("Vehicle"))
                .andExpect(jsonPath("$[0].actorEmail").value("admin@drivique.com"))
                .andExpect(jsonPath("$[0].ipAddress").value("190.24.5.10"));
    }

    @Test
    void getAuditLogs_WithFilters_PassesQueryParams() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogResponseDTO dto = new AuditLogResponseDTO(
                id, "RENTAL", "Reservation", UUID.randomUUID(), "CONFIRM", "SUCCESS",
                UUID.randomUUID(), "agent@drivique.com", "Agent User",
                null, null, "181.50.2.1",
                "Confirmación de reserva", null, "{\"status\":\"CONFIRMED\"}",
                Instant.now()
        );

        when(auditService.getAuditLogs(
                eq("Reservation"), eq("agent@drivique.com"),
                any(LocalDate.class), any(LocalDate.class),
                eq("CONFIRM"), eq("RENTAL")
        )).thenReturn(List.of(dto));

        mvc.perform(get("/v1/admin/audit-logs")
                        .param("table", "Reservation")
                        .param("user", "agent@drivique.com")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .param("operation", "CONFIRM")
                        .param("domain", "RENTAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].entityName").value("Reservation"))
                .andExpect(jsonPath("$[0].domainName").value("RENTAL"));
    }

    @Test
    void getAuditLogById_ReturnsSingleLog() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogResponseDTO dto = new AuditLogResponseDTO(
                id, "IAM", "User", UUID.randomUUID(), "PASSWORD_RESET", "SUCCESS",
                UUID.randomUUID(), "superadmin@drivique.com", "Super Admin",
                null, null, "192.168.0.1",
                "Restablecimiento forzado de contraseña", null, null,
                Instant.now()
        );

        when(auditService.getAuditLogById(id)).thenReturn(dto);

        mvc.perform(get("/v1/admin/audit-logs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.domainName").value("IAM"))
                .andExpect(jsonPath("$.operation").value("PASSWORD_RESET"))
                .andExpect(jsonPath("$.actorEmail").value("superadmin@drivique.com"));
    }
}
