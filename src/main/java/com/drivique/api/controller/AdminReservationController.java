package com.drivique.api.controller;

import com.drivique.api.dto.AdminManualReservationRequestDTO;
import com.drivique.api.dto.AdminReservationDTO;
import com.drivique.api.dto.ExpiredReservationsSummaryDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.service.AdminReservationService;
import com.drivique.api.service.ReservationExpirationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final AdminReservationService adminReservationService;

    public AdminReservationController(ReservationExpirationService reservationExpirationService, AdminReservationService adminReservationService) {
        this.reservationExpirationService = reservationExpirationService;
        this.adminReservationService = adminReservationService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'BRANCH_ADMIN')")
    @Operation(summary = "Listar reservas del panel", description = "Administradores ven todas las reservas; encargados solo las de su sucursal.")
    public List<AdminReservationDTO> list(Authentication authentication) {
        return adminReservationService.list(authentication.getName());
    }

    @PostMapping("/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'EMPLOYEE', 'BRANCH_ADMIN')")
    @Operation(summary = "Registrar reserva manual en mostrador")
    public ReservationResponseDTO createManual(@Valid @RequestBody AdminManualReservationRequestDTO request, Authentication authentication) {
        return adminReservationService.createManual(request, authentication.getName());
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
