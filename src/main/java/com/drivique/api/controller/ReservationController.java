package com.drivique.api.controller;

import com.drivique.api.dto.CreateReservationRequestDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.dto.CancelReservationRequestDTO;
import com.drivique.api.dto.ModifyReservationDatesRequestDTO;
import com.drivique.api.dto.ModifyReservationServicesRequestDTO;
import com.drivique.api.dto.AllowedTransitionsResponseDTO;
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

    @PatchMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Cancelar reserva", description = "Cancela una reserva o retención. Libera el vehículo pero si estaba confirmada no genera reembolso.")
    @ApiResponse(responseCode = "200", description = "Reserva cancelada exitosamente")
    @ApiResponse(responseCode = "400", description = "Transición inválida")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ReservationResponseDTO cancelReservation(
            @PathVariable UUID id,
            @Valid @RequestBody CancelReservationRequestDTO request,
            Authentication authentication
    ) {
        return reservationService.cancelReservation(id, request, authentication.getName());
    }

    @GetMapping("/{id}/allowed-transitions")
    @Operation(summary = "Consultar transiciones permitidas", description = "Devuelve los estados a los que puede pasar esta reserva.")
    @ApiResponse(responseCode = "200", description = "Lista de transiciones obtenida")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public AllowedTransitionsResponseDTO getAllowedTransitions(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        return reservationService.getAllowedTransitions(id, authentication.getName());
    }

    @PatchMapping("/{id}/dates")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Modificar fechas de reserva", description = "Modifica las fechas de recogida o devolución de una reserva.")
    @ApiResponse(responseCode = "200", description = "Fechas modificadas exitosamente")
    @ApiResponse(responseCode = "400", description = "Fechas inválidas o disponibilidad agotada")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ReservationResponseDTO modifyReservationDates(
            @PathVariable UUID id,
            @Valid @RequestBody ModifyReservationDatesRequestDTO request,
            Authentication authentication
    ) {
        return reservationService.modifyReservationDates(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/services")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Modificar servicios de reserva", description = "Modifica los servicios adicionales, cobertura o plan de kilometraje.")
    @ApiResponse(responseCode = "200", description = "Servicios modificados exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "404", description = "Reserva no encontrada")
    public ReservationResponseDTO modifyReservationServices(
            @PathVariable UUID id,
            @Valid @RequestBody ModifyReservationServicesRequestDTO request,
            Authentication authentication
    ) {
        return reservationService.modifyReservationServices(id, request, authentication.getName());
    }
}
