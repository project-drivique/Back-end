package com.drivique.api.repository;

import com.drivique.api.model.RentalExtensionRequest;
import com.drivique.api.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RentalExtensionRequestRepository extends JpaRepository<RentalExtensionRequest, UUID> {

    List<RentalExtensionRequest> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    List<RentalExtensionRequest> findByReservationOrderByCreatedAtDesc(Reservation reservation);

    List<RentalExtensionRequest> findByStatusIgnoreCaseOrderByCreatedAtDesc(String status);

    List<RentalExtensionRequest> findAllByOrderByCreatedAtDesc();

    boolean existsByReservationIdAndStatusIgnoreCase(UUID reservationId, String status);
}
