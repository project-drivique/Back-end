package com.drivique.api.repository;

import com.drivique.api.model.VehicleInspection;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleInspectionRepository extends JpaRepository<VehicleInspection, UUID> {
    Optional<VehicleInspection> findByContractIdAndInspectionType(UUID contractId, String inspectionType);
    boolean existsByContractIdAndInspectionType(UUID contractId, String inspectionType);
    List<VehicleInspection> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
