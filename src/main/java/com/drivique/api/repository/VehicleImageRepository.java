package com.drivique.api.repository;

import com.drivique.api.model.VehicleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleImageRepository extends JpaRepository<VehicleImage, UUID> {
    List<VehicleImage> findByVehicleIdOrderBySortOrderAsc(UUID vehicleId);
    Optional<VehicleImage> findByVehicleIdAndPrimaryTrue(UUID vehicleId);
    boolean existsByVehicleIdAndSortOrder(UUID vehicleId, short sortOrder);
    boolean existsByVehicleIdAndSortOrderAndIdNot(UUID vehicleId, short sortOrder, UUID id);
}
