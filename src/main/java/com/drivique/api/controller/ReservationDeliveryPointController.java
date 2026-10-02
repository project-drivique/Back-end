package com.drivique.api.controller;

import com.drivique.api.dto.ConfigureDeliveryPointsRequestDTO;
import com.drivique.api.dto.DeliveryPointRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import com.drivique.api.service.ReservationDeliveryPointService;
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
@RequestMapping("/v1/reservations/{id}/delivery-points")
@Tag(name = "Reservation Delivery Points", description = "Endpoints para la gestión de puntos y modalidades de recogida y devolución (Sede, Domicilio, Aeropuerto, Terminal)")
@SecurityRequirement(name = "bearerAuth")
public class ReservationDeliveryPointController {

    private final ReservationDeliveryPointService deliveryPointService;

    public ReservationDeliveryPointController(ReservationDeliveryPointService deliveryPointService) {
        this.deliveryPointService = deliveryPointService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Configurar puntos de entrega y devolución",
            description = "Registra o actualiza los puntos de recogida (PICKUP) y devolución (RETURN) para una reserva."
    )
    @ApiResponse(responseCode = "201", description = "Puntos de entrega configurados exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de modalidad o dirección inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado para modificar esta reserva")
    @ApiResponse(responseCode = "404", description = "Reserva, sede o ciudad no encontrada")
    public List<DeliveryPointResponseDTO> configureDeliveryPoints(
            @PathVariable UUID id,
            @Valid @RequestBody ConfigureDeliveryPointsRequestDTO request,
            Authentication authentication
    ) {
        return deliveryPointService.configureDeliveryPoints(id, request.points(), authentication.getName());
    }

    @GetMapping
    @Operation(
            summary = "Obtener puntos de entrega de la reserva",
            description = "Consulta los puntos de recogida y devolución configurados para una reserva específica."
    )
    @ApiResponse(responseCode = "200", description = "Puntos de entrega obtenidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado para consultar esta reserva")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public List<DeliveryPointResponseDTO> getDeliveryPoints(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return deliveryPointService.getDeliveryPointsByReservation(id, authentication.getName());
    }

    @PutMapping("/{pointType}")
    @Operation(
            summary = "Configurar punto individual de entrega o devolución",
            description = "Registra o actualiza un punto específico (PICKUP o RETURN) para la reserva."
    )
    @ApiResponse(responseCode = "200", description = "Punto de entrega configurado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de modalidad inválidos")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public DeliveryPointResponseDTO setSingleDeliveryPoint(
            @PathVariable UUID id,
            @PathVariable String pointType,
            @Valid @RequestBody DeliveryPointRequestDTO request,
            Authentication authentication
    ) {
        DeliveryPointRequestDTO normalizedRequest = new DeliveryPointRequestDTO(
                pointType,
                request.modality(),
                request.branchId(),
                request.cityId(),
                request.neighborhood(),
                request.address(),
                request.flightOrBusNumber(),
                request.referenceDetails()
        );
        return deliveryPointService.setSingleDeliveryPoint(id, normalizedRequest, authentication.getName());
    }
}
