package com.drivique.api.repository;

import com.drivique.api.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationStatusRepository extends JpaRepository<ReservationStatus, UUID> {
    Optional<ReservationStatus> findByCode(String code);
    Optional<ReservationStatus> findByCodeIgnoreCase(String code);
}
