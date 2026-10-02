package com.drivique.api.repository;

import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationDeliveryPointRepository extends JpaRepository<ReservationDeliveryPoint, UUID>, JpaSpecificationExecutor<ReservationDeliveryPoint> {

    Optional<ReservationDeliveryPoint> findByReservationIdAndPointType(UUID reservationId, String pointType);

    Optional<ReservationDeliveryPoint> findByReservationAndPointType(Reservation reservation, String pointType);

    List<ReservationDeliveryPoint> findByReservationId(UUID reservationId);

    List<ReservationDeliveryPoint> findByReservation(Reservation reservation);

    boolean existsByReservationIdAndPointType(UUID reservationId, String pointType);

    void deleteByReservationIdAndPointType(UUID reservationId, String pointType);
}
