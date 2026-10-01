package com.drivique.api.repository;

import com.drivique.api.model.VehicleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleStatusRepository extends JpaRepository<VehicleStatus, UUID> {
    List<VehicleStatus> findByActiveTrueAndAllowsReservationTrueOrderByCodeAsc();
    Optional<VehicleStatus> findByCodeIgnoreCase(String code);
}
