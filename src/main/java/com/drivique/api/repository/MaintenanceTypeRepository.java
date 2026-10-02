package com.drivique.api.repository;

import com.drivique.api.model.MaintenanceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceTypeRepository extends JpaRepository<MaintenanceType, UUID> {
    Optional<MaintenanceType> findByIdAndActiveTrue(UUID id);
}
