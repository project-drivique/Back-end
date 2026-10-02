package com.drivique.api.controller;

import com.drivique.api.dto.CreateExtensionRequestDTO;
import com.drivique.api.dto.RentalExtensionResponseDTO;
import com.drivique.api.service.RentalExtensionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/reservations/{reservationId}/extensions")
@Tag(name = "Rental Extensions", description = "Endpoints para que los clientes soliciten y consulten prórrogas/extensiones de alquiler")
@SecurityRequirement(name = "bearerAuth")
public class RentalExtensionController {

    private final RentalExtensionService extensionService;

    public RentalExtensionController(RentalExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Solicitar extensión de días de alquiler",
            description = "Permite al cliente solicitar una nueva fecha de devolución para una reserva activa, validando disponibilidad de vehículo y cotizando el valor adicional."
    )
    @ApiResponse(responseCode = "201", description = "Solicitud de extensión creada exitosamente")
    @ApiResponse(responseCode = "400", description = "Parámetros inválidos o fecha no posterior")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    @ApiResponse(responseCode = "409", description = "Conflicto por colisión de fechas o solicitud previa pendiente")
    public RentalExtensionResponseDTO requestExtension(
            @PathVariable UUID reservationId,
            @Valid @RequestBody CreateExtensionRequestDTO request,
            Authentication authentication
    ) {
        return extensionService.requestExtension(reservationId, request, authentication.getName());
    }

    @GetMapping
    @Operation(
            summary = "Listar extensiones de una reserva",
            description = "Consulta el historial de solicitudes de prórroga/extensión asociadas a la reserva."
    )
    @ApiResponse(responseCode = "200", description = "Listado de extensiones obtenido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public List<RentalExtensionResponseDTO> getExtensionsByReservation(
            @PathVariable UUID reservationId,
            Authentication authentication
    ) {
        return extensionService.getExtensionsByReservation(reservationId, authentication.getName());
    }
}
