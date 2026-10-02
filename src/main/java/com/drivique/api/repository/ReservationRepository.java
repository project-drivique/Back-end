package com.drivique.api.repository;

import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    Optional<Reservation> findByCode(String code);

    Optional<Reservation> findByCodeIgnoreCase(String code);

    List<Reservation> findByCustomerOrderByCreatedAtDesc(User customer);

    long countByCodeStartingWith(String prefix);

    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.vehicle.id = :vehicleId " +
           "AND r.blocksAvailability = true " +
           "AND r.pickupDate < :returnDate " +
           "AND r.returnDate > :pickupDate")
    boolean existsOverlappingReservation(
            @Param("vehicleId") UUID vehicleId,
            @Param("pickupDate") Instant pickupDate,
            @Param("returnDate") Instant returnDate
    );

    @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
           "WHERE r.vehicle.id = :vehicleId " +
           "AND r.id <> :excludeReservationId " +
           "AND r.blocksAvailability = true " +
           "AND r.pickupDate < :returnDate " +
           "AND r.returnDate > :pickupDate")
    boolean existsOverlappingReservationExcluding(
            @Param("vehicleId") UUID vehicleId,
            @Param("excludeReservationId") UUID excludeReservationId,
            @Param("pickupDate") Instant pickupDate,
            @Param("returnDate") Instant returnDate
    );
}
