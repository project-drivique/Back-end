package com.drivique.api.reports;

import com.drivique.api.controller.AdminReportController;
import com.drivique.api.dto.AdministrativeReportTypeResponseDTO;
import com.drivique.api.dto.GenerateReportRequestDTO;
import com.drivique.api.dto.GeneratedReportResponseDTO;
import com.drivique.api.service.ReportGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminReportControllerTests {

    private MockMvc mvc;

    @Mock
    private ReportGeneratorService reportGeneratorService;

    @Mock
    private Authentication authentication;

    private AdminReportController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminReportController(reportGeneratorService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getReportTypes_ReturnsList() throws Exception {
        AdministrativeReportTypeResponseDTO t1 = new AdministrativeReportTypeResponseDTO(
                UUID.randomUUID(), "FLEET_OCCUPANCY", "Ocupación de Flota", "Desc", true
        );
        when(reportGeneratorService.getReportTypes()).thenReturn(List.of(t1));

        mvc.perform(get("/v1/admin/reports/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("FLEET_OCCUPANCY"));
    }

    @Test
    void getReports_ReturnsList() throws Exception {
        GeneratedReportResponseDTO r1 = new GeneratedReportResponseDTO(
                UUID.randomUUID(), "FLEET_OCCUPANCY", "Ocupación de Flota", "PDF",
                UUID.randomUUID(), "Admin User", "{}", "/file.pdf", "COMPLETED",
                Instant.now(), Instant.now()
        );
        when(reportGeneratorService.getGeneratedReports(null)).thenReturn(List.of(r1));

        mvc.perform(get("/v1/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].format").value("PDF"));
    }

    @Test
    void getReportById_ReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        GeneratedReportResponseDTO r1 = new GeneratedReportResponseDTO(
                id, "REVENUE_SUMMARY", "Resumen de Ingresos", "EXCEL",
                UUID.randomUUID(), "Admin User", "{}", "/file.xlsx", "COMPLETED",
                Instant.now(), Instant.now()
        );
        when(reportGeneratorService.getReportById(id)).thenReturn(r1);

        mvc.perform(get("/v1/admin/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.format").value("EXCEL"));
    }

    @Test
    void generateReport_ValidRequest_ReturnsCreated() throws Exception {
        when(authentication.getName()).thenReturn("admin@drivique.com");

        UUID id = UUID.randomUUID();
        GeneratedReportResponseDTO r1 = new GeneratedReportResponseDTO(
                id, "FLEET_OCCUPANCY", "Ocupación de Flota", "PDF",
                UUID.randomUUID(), "Admin User", "{}", "/file.pdf", "COMPLETED",
                Instant.now(), Instant.now()
        );
        when(reportGeneratorService.generateReport(any(GenerateReportRequestDTO.class), eq("admin@drivique.com")))
                .thenReturn(r1);

        String payload = """
                {
                    "reportType": "FLEET_OCCUPANCY",
                    "format": "PDF"
                }
                """;

        mvc.perform(post("/v1/admin/reports/generate")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.reportTypeCode").value("FLEET_OCCUPANCY"));
    }

    @Test
    void downloadReport_ReturnsFileBytes() throws Exception {
        when(authentication.getName()).thenReturn("admin@drivique.com");

        UUID id = UUID.randomUUID();
        GeneratedReportResponseDTO r1 = new GeneratedReportResponseDTO(
                id, "FLEET_OCCUPANCY", "Ocupación de Flota", "PDF",
                UUID.randomUUID(), "Admin User", "{}", "/file.pdf", "COMPLETED",
                Instant.now(), Instant.now()
        );
        byte[] pdfBytes = "%PDF-1.4 mock pdf content".getBytes();

        when(reportGeneratorService.getReportById(id)).thenReturn(r1);
        when(reportGeneratorService.downloadReportContent(id, "admin@drivique.com")).thenReturn(pdfBytes);

        mvc.perform(get("/v1/admin/reports/{id}/download", id).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"fleet_occupancy_" + id + ".pdf\""))
                .andExpect(content().bytes(pdfBytes));
    }
}
