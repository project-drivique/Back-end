package com.drivique.api.controller;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.service.ReservationService;
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

/**
 * HU-INT-11: Checkout, pago aprobado y creación de la reserva.
 */
@RestController
@RequestMapping("/v1/reservations")
@Tag(name = "Reservations", description = "Endpoints para la gestión, bloqueo de disponibilidad y consulta de reservas (HU-INT-11)")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/checkout/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Iniciar checkout", description = "Crea una retención técnica (Hold) que no es una reserva confirmada y bloquea la disponibilidad temporalmente.")
    @ApiResponse(responseCode = "201", description = "Retención creada exitosamente")
    public ReservationResponseDTO initiateCheckout(
            @Valid @RequestBody CreateReservationRequestDTO request,
            Authentication authentication
    ) {
        return reservationService.createReservation(request, authentication.getName());
    }

    @PostMapping("/checkout/confirm/{holdId}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Confirmar reserva tras pago", description = "Convierte una retención técnica en una reserva oficial, generando su código.")
    @ApiResponse(responseCode = "200", description = "Reserva confirmada exitosamente")
    public ReservationResponseDTO confirmPayment(
            @PathVariable UUID holdId,
            Authentication authentication
    ) {
        return reservationService.confirmReservationPayment(holdId, authentication.getName());
    }

    @GetMapping("/me")
    @Operation(summary = "Obtener mis reservas", description = "Obtiene el historial de reservas del usuario autenticado.")
    @ApiResponse(responseCode = "200", description = "Listado de reservas obtenido")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    public List<ReservationResponseDTO> getMyReservations(Authentication authentication) {
        return reservationService.getMyReservations(authentication.getName());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener reserva por ID", description = "Obtiene los detalles completos de una reserva específica.")
    @ApiResponse(responseCode = "200", description = "Reserva encontrada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ReservationResponseDTO getReservationById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return reservationService.getReservationById(id, authentication.getName());
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Obtener reserva por código", description = "Obtiene los detalles de una reserva por su código de negocio (ej. RES-2026-0001).")
    @ApiResponse(responseCode = "200", description = "Reserva encontrada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ReservationResponseDTO getReservationByCode(
            @PathVariable String code,
            Authentication authentication
    ) {
        return reservationService.getReservationByCode(code, authentication.getName());
    }
}
