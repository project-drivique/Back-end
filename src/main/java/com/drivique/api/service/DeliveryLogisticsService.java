package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.DeliveryDriverDTO;
import com.drivique.api.dto.DeliveryDriverRequestDTO;
import com.drivique.api.dto.DeliveryPointResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Branch;
import com.drivique.api.model.DeliveryDriver;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.DeliveryDriverRepository;
import com.drivique.api.repository.ReservationDeliveryPointRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Conductores de domicilio y su asignación a las entregas y devoluciones a domicilio. */
@Service
public class DeliveryLogisticsService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeliveryDriverRepository drivers;
    private final ReservationDeliveryPointRepository points;
    private final BranchRepository branches;
    private final StaffBranchScope scope;

    public DeliveryLogisticsService(DeliveryDriverRepository drivers, ReservationDeliveryPointRepository points,
                                    BranchRepository branches, StaffBranchScope scope) {
        this.drivers = drivers;
        this.points = points;
        this.branches = branches;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public List<DeliveryDriverDTO> listDrivers(String email) {
        Optional<UUID> branchId = scope.forStaff(email);
        List<DeliveryDriver> found = branchId.isPresent()
                ? drivers.findByBranchIdOrderByFullNameAsc(branchId.get())
                : drivers.findAllByOrderByFullNameAsc();
        return found.stream().map(this::toDto).toList();
    }

    @Transactional
    @Auditable(domain = "LOCATION", entity = "DeliveryDriver", action = "INSERT", description = "Registro de conductor de domicilio", includeResult = false)
    public DeliveryDriverDTO createDriver(String email, DeliveryDriverRequestDTO request) {
        Branch branch = branchFor(email, request.branchId());
        if (drivers.existsByBranchIdAndDocumentNumber(branch.getId(), request.documentNumber().strip())) {
            throw new ConflictException("Ya existe un conductor con ese documento en la sucursal");
        }
        DeliveryDriver driver = new DeliveryDriver(branch);
        apply(driver, request);
        return toDto(drivers.saveAndFlush(driver));
    }

    @Transactional
    @Auditable(domain = "LOCATION", entity = "DeliveryDriver", action = "UPDATE", description = "Actualización de conductor de domicilio", includeResult = false)
    public DeliveryDriverDTO updateDriver(String email, UUID id, DeliveryDriverRequestDTO request) {
        DeliveryDriver driver = ownDriver(email, id);
        apply(driver, request);
        return toDto(drivers.saveAndFlush(driver));
    }

    @Transactional
    @Auditable(domain = "LOCATION", entity = "DeliveryDriver", action = "DELETE", description = "Eliminación de conductor de domicilio")
    public void deleteDriver(String email, UUID id) {
        DeliveryDriver driver = ownDriver(email, id);
        try {
            drivers.delete(driver);
            drivers.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("El conductor tiene entregas asociadas; márcalo como inactivo en lugar de eliminarlo.");
        }
    }

    @Transactional
    @Auditable(domain = "RESERVATIONS", entity = "DeliveryPoint", action = "UPDATE", description = "Asignación de conductor a una entrega a domicilio", includeResult = false)
    public DeliveryPointResponseDTO assign(String email, UUID reservationId, String pointType, UUID driverId) {
        ReservationDeliveryPoint point = homePoint(email, reservationId, pointType);
        if ("COMPLETED".equals(point.getDeliveryStatus())) {
            throw new ConflictException("La entrega ya fue completada");
        }
        DeliveryDriver driver = drivers.findById(driverId).orElseThrow(() -> new ResourceNotFoundException("Conductor no encontrado"));
        UUID pointBranch = branchOf(point);
        if (pointBranch != null && !pointBranch.equals(driver.getBranch().getId())) {
            throw new ConflictException("El conductor pertenece a otra sucursal");
        }
        if ("INACTIVE".equals(driver.getStatus())) {
            throw new ConflictException("El conductor está inactivo");
        }
        String pin = point.getDeliveryPin() != null ? point.getDeliveryPin() : String.format("%04d", RANDOM.nextInt(10000));
        point.assignDriver(driver, pin);
        driver.setStatus("ON_ROUTE");
        drivers.save(driver);
        return DeliveryPointResponseDTO.from(points.saveAndFlush(point), false);
    }

    /** El cliente entrega el PIN al conductor; con él se confirma la entrega o devolución. */
    @Transactional
    @Auditable(domain = "RESERVATIONS", entity = "DeliveryPoint", action = "UPDATE", description = "Confirmación de entrega a domicilio con PIN", includeArgs = false, includeResult = false)
    public DeliveryPointResponseDTO complete(String email, UUID reservationId, String pointType, String pin) {
        ReservationDeliveryPoint point = homePoint(email, reservationId, pointType);
        if (point.getDriver() == null) {
            throw new ConflictException("Primero asigna un conductor");
        }
        if ("COMPLETED".equals(point.getDeliveryStatus())) {
            throw new ConflictException("La entrega ya fue completada");
        }
        if (pin == null || !pin.equals(point.getDeliveryPin())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El PIN no coincide.");
        }
        point.completeDelivery();
        DeliveryDriver driver = point.getDriver();
        driver.setStatus("AVAILABLE");
        drivers.save(driver);
        return DeliveryPointResponseDTO.from(points.saveAndFlush(point), false);
    }

    private ReservationDeliveryPoint homePoint(String email, UUID reservationId, String pointType) {
        ReservationDeliveryPoint point = points.findByReservationIdAndPointType(reservationId, pointType.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Punto de entrega no encontrado"));
        if (!"HOME_DELIVERY".equals(point.getModality())) {
            throw new ConflictException("La reserva no tiene entrega a domicilio en este punto");
        }
        Optional<UUID> staffBranch = scope.forStaff(email);
        UUID pointBranch = branchOf(point);
        if (staffBranch.isPresent() && pointBranch != null && !staffBranch.get().equals(pointBranch)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La reserva pertenece a otra sucursal.");
        }
        return point;
    }

    /** Sucursal responsable del domicilio: la del vehículo de la reserva. */
    private UUID branchOf(ReservationDeliveryPoint point) {
        var vehicle = point.getReservation().getVehicle();
        return vehicle != null && vehicle.getCurrentBranch() != null ? vehicle.getCurrentBranch().getId() : null;
    }

    private Branch branchFor(String email, UUID requestedBranchId) {
        UUID branchId = scope.forStaff(email).orElse(requestedBranchId);
        if (branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la sucursal del conductor.");
        }
        return branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));
    }

    private DeliveryDriver ownDriver(String email, UUID id) {
        DeliveryDriver driver = drivers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conductor no encontrado"));
        Optional<UUID> staffBranch = scope.forStaff(email);
        if (staffBranch.isPresent() && !staffBranch.get().equals(driver.getBranch().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El conductor pertenece a otra sucursal.");
        }
        return driver;
    }

    private void apply(DeliveryDriver driver, DeliveryDriverRequestDTO request) {
        driver.setFullName(request.fullName().strip());
        driver.setEmail(blankToNull(request.email()));
        driver.setPhone(blankToNull(request.phone()));
        driver.setDocumentType(request.documentType());
        driver.setDocumentNumber(request.documentNumber().strip());
        driver.setLicenseNumber(blankToNull(request.licenseNumber()));
        driver.setLicenseCategory(blankToNull(request.licenseCategory()));
        driver.setLicenseExpiresOn(request.licenseExpiresOn());
        driver.setVehicleDescription(blankToNull(request.vehicleDescription()));
        if (request.status() != null) {
            driver.setStatus(request.status());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private DeliveryDriverDTO toDto(DeliveryDriver d) {
        return new DeliveryDriverDTO(d.getId(), d.getBranch().getId(), d.getBranch().getName(), d.getFullName(), d.getEmail(),
                d.getPhone(), d.getDocumentType(), d.getDocumentNumber(), d.getLicenseNumber(), d.getLicenseCategory(),
                d.getLicenseExpiresOn(), d.getVehicleDescription(), d.getStatus());
    }
}
