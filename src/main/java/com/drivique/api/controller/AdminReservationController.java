package com.drivique.api.controller;

import com.drivique.api.dto.ExpiredReservationsSummaryDTO;
import com.drivique.api.service.ReservationExpirationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/reservations")
@Tag(name = "Admin Reservations", description = "Endpoints administrativos para gestión y operaciones sobre reservas")
@SecurityRequirement(name = "bearerAuth")
public class AdminReservationController {

    private final ReservationExpirationService reservationExpirationService;

    public AdminReservationController(ReservationExpirationService reservationExpirationService) {
        this.reservationExpirationService = reservationExpirationService;
    }

    @PostMapping("/expire-pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'BRANCH_ADMIN') or hasAuthority('rental:manage')")
    @Operation(
            summary = "Ejecutar expiración manual de reservas pendientes",
            description = "Dispara el proceso de expiración para todas las reservas en estado PENDING_PAYMENT cuya fecha límite haya vencido, liberando la disponibilidad del vehículo."
    )
    @ApiResponse(responseCode = "200", description = "Proceso de expiración ejecutado exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public ExpiredReservationsSummaryDTO triggerExpirePending() {
        return reservationExpirationService.expirePendingReservations();
    }
}
