package com.drivique.api.repository;

import com.drivique.api.model.VehicleMaintenance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleMaintenanceRepository extends JpaRepository<VehicleMaintenance, UUID> {
    List<VehicleMaintenance> findByVehicleIdOrderByScheduledDateDesc(UUID vehicleId);
}
