package com.drivique.api.repository;

import com.drivique.api.model.ReservationAdditionalService;
import com.drivique.api.model.ReservationAdditionalServiceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationAdditionalServiceRepository extends JpaRepository<ReservationAdditionalService, ReservationAdditionalServiceId> {
    List<ReservationAdditionalService> findByReservationId(UUID reservationId);
}
