package com.drivique.api.controller;

import com.drivique.api.dto.AuditLogResponseDTO;
import com.drivique.api.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/audit-logs")
@Tag(name = "Admin Audit Logs", description = "Trazabilidad forense y consulta de bitácoras transaccionales de auditoría")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAuditLogController {

    private final AuditService auditService;

    public AdminAuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(
            summary = "Consultar bitácoras de auditoría forense",
            description = "Obtiene registros de auditoría filtrados por tabla/entidad, usuario, rango de fechas, operación o dominio."
    )
    @ApiResponse(responseCode = "200", description = "Listado de logs de auditoría obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public List<AuditLogResponseDTO> getAuditLogs(
            @RequestParam(value = "table", required = false) String table,
            @RequestParam(value = "entityName", required = false) String entityName,
            @RequestParam(value = "user", required = false) String user,
            @RequestParam(value = "userEmail", required = false) String userEmail,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate,
            @RequestParam(value = "operation", required = false) String operation,
            @RequestParam(value = "domain", required = false) String domain
    ) {
        String entityFilter = entityName != null ? entityName : table;
        String userFilter = userEmail != null ? userEmail : user;
        return auditService.getAuditLogs(entityFilter, userFilter, startDate, endDate, operation, domain);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar detalle de un registro de auditoría",
            description = "Obtiene los snapshots completos (old_data, new_data) y metadatos forenses de una entrada de auditoría."
    )
    @ApiResponse(responseCode = "200", description = "Registro de auditoría encontrado")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Registro de auditoría no encontrado")
    public AuditLogResponseDTO getAuditLogById(@PathVariable UUID id) {
        return auditService.getAuditLogById(id);
    }
}
