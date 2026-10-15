package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.AdminManualReservationRequestDTO;
import com.drivique.api.dto.AdminReservationDTO;
import com.drivique.api.dto.ReservationResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminReservationService {

    private final ReservationRepository reservations;
    private final UserRepository users;
    private final BranchUserRepository branchUsers;
    private final RentalContractRepository contracts;
    private final PaymentRepository payments;
    private final VehicleRepository vehicles;
    private final ReservationService reservationService;
    private final StaffBranchScope scope;

    public AdminReservationService(ReservationRepository reservations, UserRepository users,
            BranchUserRepository branchUsers, RentalContractRepository contracts, PaymentRepository payments,
            VehicleRepository vehicles, ReservationService reservationService, StaffBranchScope scope) {
        this.reservations = reservations;
        this.users = users;
        this.branchUsers = branchUsers;
        this.contracts = contracts;
        this.payments = payments;
        this.vehicles = vehicles;
        this.reservationService = reservationService;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<AdminReservationDTO> list(String staffEmail) {
        Optional<UUID> branchScope = scope.forStaff(staffEmail);
        return reservations.findAll().stream()
                .filter(r -> branchScope.isEmpty() || belongsToBranch(r, branchScope.get()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional
    @Auditable(domain = "RESERVATIONS", entity = "Reservation", action = "INSERT", description = "Reserva manual creada en sucursal", includeResult = false)
    public ReservationResponseDTO createManual(AdminManualReservationRequestDTO request, String staffEmail) {
        Optional<UUID> branchScope = scope.forStaff(staffEmail);
        if (branchScope.isPresent()) {
            Vehicle vehicle = vehicles.findById(request.reservation().vehicleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehículo no encontrado"));
            if (vehicle.getCurrentBranch() == null || !branchScope.get().equals(vehicle.getCurrentBranch().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes registrar reservas de vehículos de tu sucursal.");
            }
        }
        User customer = users.findByEmailIgnoreCaseAndDeletedAtIsNull(request.customerEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un cliente registrado con ese correo."));
        return reservationService.createReservation(request.reservation(), customer.getEmail());
    }

    private AdminReservationDTO toAdminDto(Reservation r) {
        Branch branch = r.getVehicle() != null ? r.getVehicle().getCurrentBranch() : null;
        Optional<RentalContract> contract = contracts.findByReservationId(r.getId());
        Optional<Payment> payment = contract.flatMap(c -> payments.findByContractId(c.getId()).stream()
                .filter(p -> p.getStatus() != null && "APPROVED".equalsIgnoreCase(p.getStatus().getCode()))
                .findFirst());
        return new AdminReservationDTO(
                reservationService.toResponse(r),
                r.getCustomer().getPhone(),
                r.getCustomer().getDocumentNumber(),
                branch != null ? branch.getId() : null,
                branch != null ? branch.getName() : null,
                branch != null && branch.getCity() != null ? branch.getCity().getName() : null,
                r.getVehicle() != null ? r.getVehicle().getMainImageUrl() : null,
                payment.map(p -> p.getPaymentMethod().getCode()).orElse(r.getCashPaymentCode() != null ? "CASH" : "WOMPI"),
                payment.isPresent() ? "APPROVED" : "PENDING",
                payment.map(Payment::getAmount).orElse(null),
                payment.map(Payment::getPaidAt).orElse(null),
                contract.map(RentalContract::getId).orElse(null),
                contract.map(RentalContract::getContractNumber).orElse(null),
                contract.map(c -> c.getSignedAt() != null).orElse(false));
    }

    private boolean belongsToBranch(Reservation r, UUID branchId) {
        boolean vehicleBranch = r.getVehicle() != null && r.getVehicle().getCurrentBranch() != null
                && branchId.equals(r.getVehicle().getCurrentBranch().getId());
        boolean cashBranch = r.getCashPaymentBranch() != null && branchId.equals(r.getCashPaymentBranch().getId());
        return vehicleBranch || cashBranch;
    }
}
