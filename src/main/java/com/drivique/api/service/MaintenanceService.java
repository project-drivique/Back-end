package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {
    private final VehicleMaintenanceRepository maintenances;
    private final VehicleRepository vehicles;
    private final MaintenanceTypeRepository types;
    private final VehicleStatusRepository statuses;

    public MaintenanceService(VehicleMaintenanceRepository maintenances, VehicleRepository vehicles,
                              MaintenanceTypeRepository types, VehicleStatusRepository statuses) {
        this.maintenances = maintenances; this.vehicles = vehicles; this.types = types; this.statuses = statuses;
    }

    @Transactional
    public VehicleMaintenanceResponseDTO schedule(MaintenanceScheduleRequestDTO input) {
        Vehicle vehicle = vehicles.findById(input.vehicleId()).orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
        MaintenanceType type = types.findByIdAndActiveTrue(input.maintenanceTypeId()).orElseThrow(() -> new ResourceNotFoundException("Active maintenance type not found"));
        vehicle.setStatus(status("MAINTENANCE"));
        return toDto(maintenances.saveAndFlush(new VehicleMaintenance(vehicle, type, input.scheduledDate(), input.cost(), clean(input.description()))));
    }

    @Transactional
    public VehicleMaintenanceResponseDTO complete(UUID id, MaintenanceCompletionRequestDTO input) {
        VehicleMaintenance maintenance = maintenances.findById(id).orElseThrow(() -> new ResourceNotFoundException("Maintenance not found"));
        if (input.completedDate().isBefore(maintenance.getScheduledDate())) throw new IllegalArgumentException("Completion date cannot precede scheduled date");
        maintenance.complete(input.completedDate(), input.cost());
        maintenance.getVehicle().setStatus(status("AVAILABLE"));
        return toDto(maintenances.saveAndFlush(maintenance));
    }

    @Transactional(readOnly = true)
    public List<VehicleMaintenanceResponseDTO> history(UUID vehicleId) {
        if (!vehicles.existsById(vehicleId)) throw new ResourceNotFoundException("Vehicle not found");
        return maintenances.findByVehicleIdOrderByScheduledDateDesc(vehicleId).stream().map(this::toDto).toList();
    }

    private VehicleStatus status(String code) {
        return statuses.findByCodeIgnoreCase(code).orElseThrow(() -> new ResourceNotFoundException("Vehicle status " + code + " not found"));
    }
    private VehicleMaintenanceResponseDTO toDto(VehicleMaintenance item) {
        return new VehicleMaintenanceResponseDTO(item.getId(), item.getVehicle().getId(), item.getType().getCode(), item.getType().getName(), item.getScheduledDate(), item.getCompletedDate(), item.getCost(), item.getDescription());
    }
    private String clean(String value) { return value == null ? null : value.strip(); }
}
