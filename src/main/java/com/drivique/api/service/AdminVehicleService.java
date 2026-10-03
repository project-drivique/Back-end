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
        Page<Vehicle> page = vehicles.findAll(pageable);
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
        return vehicles.findById(id)
                .map(VehicleMapper::toAdminDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "CREATE", description = "Creación de vehículo")
    public VehicleAdminResponseDTO create(VehicleCreateRequestDTO input) {
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

        if (input.mileage() < vehicle.getMileage()) {
            throw new IllegalArgumentException(
                    "New mileage (" + input.mileage() + ") cannot be lower than current vehicle mileage (" + vehicle.getMileage() + ")"
            );
        }

        Branch branch = branches.findById(input.currentBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + input.currentBranchId()));

        vehicle.setMileage(input.mileage());
        vehicle.setDailyRate(input.dailyRate());
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

        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "UPDATE_STATUS", description = "Cambio de estado de vehículo")
    public VehicleAdminResponseDTO updateStatus(UUID id, VehicleStatusUpdateRequestDTO input) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));

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
        vehicle.toggleActive();
        return VehicleMapper.toAdminDTO(vehicles.saveAndFlush(vehicle));
    }

    @Transactional
    @Auditable(domain = "FLEET", entity = "Vehicle", action = "DELETE", description = "Eliminación lógica de vehículo")
    public void delete(UUID id) {
        Vehicle vehicle = vehicles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + id));
        // Logical soft deletion prevents breaking historical integrity with rentals and contracts
        vehicle.setActive(false);
        vehicles.saveAndFlush(vehicle);
    }
}
