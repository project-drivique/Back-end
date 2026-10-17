package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.*;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleMapper;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminVehicleService {

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StaffBranchScope staffScope;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.drivique.api.repository.ReservationRepository reservations;

    private java.util.Optional<UUID> currentScope() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (staffScope == null || authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return java.util.Optional.empty();
        }
        return staffScope.forAuthentication(authentication);
    }

    private void assertBranchAllowed(UUID branchId) {
        currentScope().ifPresent(scope -> {
            if (branchId == null || !scope.equals(branchId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "Solo puedes gestionar vehículos de tu sucursal.");
            }
        });
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private BranchCategoryService branchCategories;

    private void assertCategoryOffered(UUID branchId, UUID categoryId) {
        if (branchCategories != null && branchId != null && categoryId != null && !branchCategories.isEnabled(branchId, categoryId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "La sucursal no tiene habilitada esa categoría de vehículo.");
        }
    }

    private void assertVehicleAllowed(Vehicle vehicle) {
        assertBranchAllowed(vehicle.getCurrentBranch() != null ? vehicle.getCurrentBranch().getId() : null);
    }


    private final VehicleRepository vehicles;
    private final VehicleBrandRepository brands;
    private final VehicleCategoryRepository categories;
    private final TransmissionTypeRepository transmissions;
    private final FuelTypeRepository fuels;
    private final VehicleStatusRepository statuses;
    private final BranchRepository branches;

    public AdminVehicleService(
            VehicleRepository vehicles,
            VehicleBrandRepository brands,
            VehicleCategoryRepository categories,
            TransmissionTypeRepository transmissions,
            FuelTypeRepository fuels,
            VehicleStatusRepository statuses,
            BranchRepository branches
    ) {
        this.vehicles = vehicles;
        this.brands = brands;
        this.categories = categories;
        this.transmissions = transmissions;
        this.fuels = fuels;
        this.statuses = statuses;
        this.branches = branches;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<VehicleAdminResponseDTO> list(Pageable pageable) {
        java.util.Optional<UUID> scope = currentScope();
        Page<Vehicle> page = scope.isPresent()
                ? vehicles.findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("currentBranch").get("id"), scope.get()),
                        cb.isTrue(root.get("active"))
                  ), pageable)
                : vehicles.findAll((root, query, cb) -> cb.isTrue(root.get("active")), pageable);
        return new PageResponseDTO<>(
                page.getContent().stream().map(VehicleMapper::toAdminDTO).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @Transactional(readOnly = true)
    public VehicleAdminResponseDTO detail(UUID id) {
        Vehicle found = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        assertVehicleAllowed(found);
        return VehicleMapper.toAdminDTO(found);
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "CREATE", description = "Creación de vehículo")
    public VehicleAdminResponseDTO create(VehicleCreateRequestDTO input) {
        assertBranchAllowed(input.currentBranchId());
        assertCategoryOffered(input.currentBranchId(), input.categoryId());
        String plate = input.plate().strip().toUpperCase();
        String vin = input.vin().strip().toUpperCase();

        if (vehicles.existsByPlateIgnoreCase(plate)) {
            throw new ConflictException("Vehicle with plate " + plate + " already exists");
        }
        if (vehicles.existsByVinIgnoreCase(vin)) {
            throw new ConflictException("Vehicle with VIN " + vin + " already exists");
        }

        VehicleBrand brand = brands.findById(input.brandId())
                .orElseThrow(() -> new ResourceNotFoundException("VehicleBrand not found: " + input.brandId()));
        VehicleCategory category = categories.findById(input.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("VehicleCategory not found: " + input.categoryId()));
        TransmissionType transmission = transmissions.findById(input.transmissionTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("TransmissionType not found: " + input.transmissionTypeId()));
        FuelType fuel = fuels.findById(input.fuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("FuelType not found: " + input.fuelTypeId()));
        Branch branch = branches.findById(input.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + input.currentBranchId()));

        VehicleStatus status;
        if (input.statusId() != null) {
            status = statuses.findById(input.statusId())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleStatus not found: " + input.statusId()));
        } else {
            status = statuses.findByCodeIgnoreCase("AVAILABLE")
                    .orElseGet(() -> statuses.findAll().stream().findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("No vehicle status available in catalog")));
        }

        Vehicle vehicle = new Vehicle(
                plate,
                vin,
                brand,
                category,
                transmission,
                fuel,
                status,
                branch,
                input.model().strip(),
                input.year(),
                input.color() != null ? input.color().strip() : null,
                input.passengerCapacity(),
                input.doorsCount(),
                input.trunkCapacityLiters(),
                input.mileage(),
                input.dailyRate(),
                input.mainImageUrl() != null ? input.mainImageUrl().strip() : null,
                Boolean.TRUE.equals(input.isFeatured())
        );

        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "UPDATE", description = "Actualización de vehículo")
    public VehicleAdminResponseDTO update(UUID id, VehicleUpdateRequestDTO input) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        assertVehicleAllowed(vehicle);
        assertBranchAllowed(input.currentBranchId());

        if (input.mileage() != null && input.mileage() < vehicle.getMileage()) {
            throw new IllegalArgumentException(
                    "New mileage (" + input.mileage() + ") cannot be lower than current vehicle mileage (" + vehicle.getMileage() + ")"
            );
        }

        Branch branch = branches.findById(input.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + input.currentBranchId()));

        if (input.mileage() != null) {
            vehicle.setMileage(input.mileage());
        }
        if (input.dailyRate() != null) {
            vehicle.setDailyRate(input.dailyRate());
        }
        vehicle.setCurrentBranch(branch);

        if (input.statusId() != null) {
            VehicleStatus status = statuses.findById(input.statusId())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleStatus not found: " + input.statusId()));
            vehicle.setStatus(status);
        }

        if (input.color() != null) {
            vehicle.setColor(input.color().strip());
        }
        if (input.mainImageUrl() != null) {
            vehicle.setMainImageUrl(input.mainImageUrl().strip());
        }
        if (input.isFeatured() != null) {
            vehicle.setFeatured(input.isFeatured());
        }
        if (input.passengerCapacity() != null) {
            vehicle.setPassengerCapacity(input.passengerCapacity());
        }
        if (input.doorsCount() != null) {
            vehicle.setDoorsCount(input.doorsCount());
        }
        if (input.trunkCapacityLiters() != null) {
            vehicle.setTrunkCapacityLiters(input.trunkCapacityLiters());
        }

        if (input.model() != null && !input.model().isBlank()) {
            vehicle.setModel(input.model().strip());
        }
        if (input.year() != null) {
            vehicle.setYear(input.year().shortValue());
        }
        if (input.brandId() != null) {
            VehicleBrand brand = brands.findById(input.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleBrand not found: " + input.brandId()));
            vehicle.setBrand(brand);
        }
        if (input.categoryId() != null) {
            VehicleCategory category = categories.findById(input.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleCategory not found: " + input.categoryId()));
            vehicle.setCategory(category);
        }
        if (input.transmissionTypeId() != null) {
            TransmissionType transmission = transmissions.findById(input.transmissionTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("TransmissionType not found: " + input.transmissionTypeId()));
            vehicle.setTransmissionType(transmission);
        }
        if (input.fuelTypeId() != null) {
            FuelType fuel = fuels.findById(input.fuelTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("FuelType not found: " + input.fuelTypeId()));
            vehicle.setFuelType(fuel);
        }
        if (input.plate() != null && !input.plate().isBlank()) {
            String p = input.plate().strip().toUpperCase();
            if (vehicles.existsByPlateIgnoreCaseAndIdNot(p, vehicle.getId())) {
                throw new ConflictException("Vehicle with plate " + p + " already exists");
            }
            vehicle.setPlate(p);
        }
        if (input.vin() != null && !input.vin().isBlank()) {
            String v = input.vin().strip().toUpperCase();
            if (vehicles.existsByVinIgnoreCaseAndIdNot(v, vehicle.getId())) {
                throw new ConflictException("Vehicle with VIN " + v + " already exists");
            }
            vehicle.setVin(v);
        }

        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "UPDATE_STATUS", description = "Cambio de estado de vehículo")
    public VehicleAdminResponseDTO updateStatus(UUID id, VehicleStatusUpdateRequestDTO input) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        assertVehicleAllowed(vehicle);

        VehicleStatus status;
        if (input.statusId() != null) {
            status = statuses.findById(input.statusId())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleStatus not found: " + input.statusId()));
        } else if (input.statusCode() != null && !input.statusCode().isBlank()) {
            status = statuses.findByCodeIgnoreCase(input.statusCode().strip())
                    .orElseThrow(() -> new ResourceNotFoundException("VehicleStatus not found with code: " + input.statusCode()));
        } else {
            throw new IllegalArgumentException("Status ID or status code must be provided");
        }

        vehicle.setStatus(status);
        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    public VehicleAdminResponseDTO toggleStatus(UUID id) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        assertVehicleAllowed(vehicle);
        vehicle.toggleActive();
        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "DELETE", description = "Eliminación lógica de vehículo")
    public void delete(UUID id) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        assertVehicleAllowed(vehicle);
        if (reservations != null && reservations.existsActiveByVehicle(vehicle.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "No se puede eliminar el vehículo porque tiene reservas activas.");
        }
        // Logical soft deletion prevents breaking historical integrity with rentals and contracts
        vehicle.setActive(false);
        vehicles.saveAndFlush(vehicle);
    }
}
