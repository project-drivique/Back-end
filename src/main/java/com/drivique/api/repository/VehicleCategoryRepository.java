package com.drivique.api.repository;

import com.drivique.api.model.VehicleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleCategoryRepository extends JpaRepository<VehicleCategory, UUID> {
    List<VehicleCategory> findByActiveTrueOrderByNameAsc();
    Optional<VehicleCategory> findByIdAndActiveTrue(UUID id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
