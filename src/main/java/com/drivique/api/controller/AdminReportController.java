package com.drivique.api.controller;

import com.drivique.api.dto.AdministrativeReportTypeResponseDTO;
import com.drivique.api.dto.GenerateReportRequestDTO;
import com.drivique.api.dto.GeneratedReportResponseDTO;
import com.drivique.api.service.ReportGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/reports")
@Tag(name = "Admin Reports", description = "Generación y exportación de reportes administrativos (PDF, Excel, CSV)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'BRANCH_ADMIN')")
public class AdminReportController {

    private final ReportGeneratorService reportGeneratorService;

    public AdminReportController(ReportGeneratorService reportGeneratorService) {
        this.reportGeneratorService = reportGeneratorService;
    }

    @GetMapping("/types")
    @Operation(summary = "Listar tipos de reportes administrativos", description = "Obtiene el catálogo de tipos de reportes disponibles.")
    @ApiResponse(responseCode = "200", description = "Listado de tipos de reportes obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public List<AdministrativeReportTypeResponseDTO> getReportTypes() {
        return reportGeneratorService.getReportTypes();
    }

    @GetMapping
    @Operation(summary = "Historial de reportes generados", description = "Consulta la lista histórica de reportes generados con filtro opcional por tipo.")
    @ApiResponse(responseCode = "200", description = "Historial de reportes obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public List<GeneratedReportResponseDTO> getReports(
            @RequestParam(value = "reportType", required = false) String reportType
    ) {
        return reportGeneratorService.getGeneratedReports(reportType);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar detalle de un reporte generado", description = "Obtiene la información y metadatos de un reporte generado por su ID.")
    @ApiResponse(responseCode = "200", description = "Reporte encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Reporte no encontrado")
    public GeneratedReportResponseDTO getReportById(@PathVariable UUID id) {
        return reportGeneratorService.getReportById(id);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Generar nuevo reporte administrativo", description = "Genera un reporte analítico en formato PDF, Excel o CSV, y lo registra en el historial.")
    @ApiResponse(responseCode = "201", description = "Reporte generado y almacenado exitosamente")
    @ApiResponse(responseCode = "400", description = "Parámetros o formato inválido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public GeneratedReportResponseDTO generateReport(
            @Valid @RequestBody GenerateReportRequestDTO request,
            Authentication authentication
    ) {
        return reportGeneratorService.generateReport(request, authentication.getName());
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Descargar archivo de reporte", description = "Descarga los bytes del archivo generado (PDF, XLSX o CSV).")
    @ApiResponse(responseCode = "200", description = "Archivo descargado exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Reporte no encontrado")
    public ResponseEntity<byte[]> downloadReport(@PathVariable UUID id, Authentication authentication) {
        GeneratedReportResponseDTO report = reportGeneratorService.getReportById(id);
        byte[] content = reportGeneratorService.downloadReportContent(id, authentication.getName());

        String filename = report.reportTypeCode().toLowerCase() + "_" + report.id();
        MediaType mediaType;

        switch (report.format().toUpperCase()) {
            case "EXCEL" -> {
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                filename += ".xlsx";
            }
            case "CSV" -> {
                mediaType = MediaType.parseMediaType("text/csv");
                filename += ".csv";
            }
            default -> {
                mediaType = MediaType.APPLICATION_PDF;
                filename += ".pdf";
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }
}
