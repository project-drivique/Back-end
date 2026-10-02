package com.drivique.api.controller;

import com.drivique.api.dto.RentalExtensionResponseDTO;
import com.drivique.api.dto.ReviewExtensionRequestDTO;
import com.drivique.api.service.RentalExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/extensions")
@Tag(name = "Admin Rental Extensions", description = "Endpoints administrativos para listar, revisar y dictaminar solicitudes de prórroga")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'BRANCH_ADMIN') or hasAuthority('rental:manage')")
public class AdminRentalExtensionController {

    private final RentalExtensionService extensionService;

    public AdminRentalExtensionController(RentalExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @GetMapping
    @Operation(
            summary = "Listar todas las solicitudes de extensión",
            description = "Recupera las solicitudes de prórroga con opción de filtrar por estado (PENDING, APPROVED, REJECTED)."
    )
    @ApiResponse(responseCode = "200", description = "Listado obtenido exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public List<RentalExtensionResponseDTO> listExtensions(
            @RequestParam(required = false) String status
    ) {
        return extensionService.getAllExtensions(status);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener detalle de solicitud de extensión",
            description = "Consulta los detalles de una solicitud de extensión específica por su ID."
    )
    @ApiResponse(responseCode = "200", description = "Detalle obtenido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    public RentalExtensionResponseDTO getExtensionById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return extensionService.getExtensionById(id, authentication.getName());
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Aprobar o rechazar solicitud de extensión",
            description = "Actualiza el estado de la solicitud a APPROVED o REJECTED. Si es aprobada, actualiza la fecha de devolución (return_date) y monto estimado en la reserva."
    )
    @ApiResponse(responseCode = "200", description = "Dictamen procesado exitosamente")
    @ApiResponse(responseCode = "400", description = "Estado inválido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Solicitud no encontrada")
    @ApiResponse(responseCode = "409", description = "Conflicto por colisión de fechas o solicitud previamente procesada")
    public RentalExtensionResponseDTO reviewExtension(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewExtensionRequestDTO request,
            Authentication authentication
    ) {
        return extensionService.reviewExtension(id, request, authentication.getName());
    }
}
