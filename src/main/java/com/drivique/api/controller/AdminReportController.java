package com.drivique.api.controller;

import com.drivique.api.dto.AdministrativeReportTypeResponseDTO;
import com.drivique.api.dto.GenerateReportRequestDTO;
import com.drivique.api.dto.GeneratedReportResponseDTO;
import com.drivique.api.dto.ReportDashboardDTO;
import com.drivique.api.dto.ReportDataDTO;
import com.drivique.api.dto.ReportExportRequestDTO;
import com.drivique.api.dto.ReportHistoryDTO;
import com.drivique.api.dto.ReportHistoryRequestDTO;
import com.drivique.api.service.ReportDashboardService;
import com.drivique.api.service.ReportDataService;
import com.drivique.api.service.ReportDataService.ReportFilter;
import com.drivique.api.service.ReportExportService;
import com.drivique.api.service.ReportGeneratorService;
import com.drivique.api.service.ReportHistoryService;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/reports")
@Tag(name = "Admin Reports", description = "Generación y exportación de reportes administrativos (PDF, Excel, CSV)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'BRANCH_ADMIN')")
public class AdminReportController {

    private final ReportGeneratorService reportGeneratorService;
    private final ReportHistoryService historyService;
    private final ReportDataService dataService;
    private final ReportExportService exportService;
    private final ReportDashboardService dashboardService;

    public AdminReportController(ReportGeneratorService reportGeneratorService, ReportHistoryService historyService,
                                 ReportDataService dataService, ReportExportService exportService,
                                 ReportDashboardService dashboardService) {
        this.reportGeneratorService = reportGeneratorService;
        this.historyService = historyService;
        this.dataService = dataService;
        this.exportService = exportService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Tablero de reportes calculado en el servidor", description = "Ingresos, flota, pagos y desempeño por sucursal; un encargado solo ve su sucursal.")
    public ReportDashboardDTO dashboard(Authentication authentication) {
        return dashboardService.dashboard(authentication.getName());
    }

    @GetMapping("/data/{type}")
    @Operation(summary = "Consultar un reporte calculado en el servidor",
            description = "Tipos: RESERVATIONS, REVENUE_SUMMARY, FLEET_OCCUPANCY, VEHICLES, BRANCHES, INCIDENTS. Con filtros y paginación; un encargado solo ve su sucursal.")
    public ReportDataDTO data(@PathVariable String type,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String category, @RequestParam(required = false) String priority,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return dataService.page(type, new ReportFilter(from, to, branchId, status, category, priority, search), page, size,
                authentication.getName());
    }

    @PostMapping("/export")
    @Operation(summary = "Generar y descargar un reporte (PDF, Excel o CSV)",
            description = "El servidor calcula el reporte, genera el archivo y lo registra en el historial y la auditoría. Supera el límite de filas: 422.")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ReportExportRequestDTO request, Authentication authentication) {
        ReportFilter filter = new ReportFilter(request.from(), request.to(), request.branchId(), request.status(),
                request.category(), request.priority(), request.search());
        ReportDataDTO data = dataService.exportAll(request.type(), filter, authentication.getName());
        ReportExportService.Document2 document = exportService.fromData(data, filterSummary(data));
        byte[] file = exportService.render(document, request.format());
        ReportHistoryDTO saved = historyService.save(new ReportHistoryRequestDTO(data.type(), data.title(), data.title(),
                request.format(), (int) data.totalElements(), new java.util.LinkedHashMap<>(data.filters()),
                exportService.snapshot(document)), authentication.getName());
        return fileResponse(file, request.format(), data.type().toLowerCase() + "_" + saved.code(), saved);
    }

    @GetMapping("/history/{id}/download")
    @Operation(summary = "Descargar de nuevo un reporte del historial", description = "Regenera el archivo en el servidor a partir de lo guardado, sin recalcular datos.")
    @SuppressWarnings("unchecked")
    public ResponseEntity<byte[]> downloadFromHistory(@PathVariable UUID id,
            @RequestParam(required = false) String format, Authentication authentication) {
        ReportHistoryDTO report = historyService.get(id, authentication.getName());
        String chosen = format == null || format.isBlank() ? report.format() : format.toUpperCase();
        if (!List.of("PDF", "EXCEL", "CSV").contains(chosen)) {
            chosen = "PDF";
        }
        byte[] file = exportService.render(exportService.fromSnapshot((Map<String, Object>) report.snapshot()), chosen);
        return fileResponse(file, chosen, report.typeCode().toLowerCase() + "_" + report.code(), report);
    }

    private ResponseEntity<byte[]> fileResponse(byte[] file, String format, String baseName, ReportHistoryDTO saved) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportService.contentType(format)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + "." + exportService.extension(format) + "\"")
                .header("X-Report-Id", saved.id().toString())
                .header("X-Report-Code", saved.code())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, X-Report-Id, X-Report-Code")
                .body(file);
    }

    private List<Map<String, String>> filterSummary(ReportDataDTO data) {
        List<Map<String, String>> out = new ArrayList<>();
        Map<String, Object> f = data.filters();
        if (f.get("from") != null || f.get("to") != null) {
            out.add(Map.of("label", "Periodo", "value", (f.get("from") != null ? f.get("from") : "inicio") + " al " + (f.get("to") != null ? f.get("to") : "hoy")));
        }
        if (f.get("branchId") != null) {
            out.add(Map.of("label", "Sucursal", "value", String.valueOf(dataService.branchName((UUID) f.get("branchId")))));
        }
        for (String key : List.of("status", "category", "priority", "search")) {
            if (f.get(key) != null && !String.valueOf(f.get(key)).isBlank()) {
                String label = switch (key) { case "status" -> "Estado"; case "category" -> "Categoría"; case "priority" -> "Prioridad"; default -> "Búsqueda"; };
                out.add(Map.of("label", label, "value", String.valueOf(f.get(key))));
            }
        }
        return out;
    }

    @GetMapping("/history")
    public List<ReportHistoryDTO> history(Authentication authentication) {
        return historyService.list(authentication.getName());
    }

    @GetMapping("/history/{id}")
    public ReportHistoryDTO historyDetail(@PathVariable UUID id, Authentication authentication) {
        return historyService.get(id, authentication.getName());
    }

    @PostMapping("/history")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportHistoryDTO saveHistory(@Valid @RequestBody ReportHistoryRequestDTO request, Authentication authentication) {
        return historyService.save(request, authentication.getName());
    }

    @DeleteMapping("/history/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@PathVariable UUID id, Authentication authentication) {
        historyService.delete(id, authentication.getName());
    }

    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(Authentication authentication) {
        historyService.clear(authentication.getName());
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
