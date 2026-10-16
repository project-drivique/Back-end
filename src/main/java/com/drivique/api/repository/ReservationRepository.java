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
    Optional<Reservation> findByCashPaymentCodeIgnoreCase(String cashPaymentCode);
    List<Reservation> findByCashPaymentCodeIsNotNullOrderByCreatedAtDesc();
    List<Reservation> findByCashPaymentBranchIdAndCashPaymentCodeIsNotNullOrderByCreatedAtDesc(UUID branchId);

    List<Reservation> findByCustomerOrderByCreatedAtDesc(User customer);

    boolean existsByCustomerId(UUID customerId);
    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.vehicle.id = :vehicleId AND r.blocksAvailability = true")
    boolean existsActiveByVehicle(@Param("vehicleId") UUID vehicleId);
    boolean existsByCustomer(User customer);

    long countByCodeStartingWith(String prefix);

    long countByCustomerIdAndStatusCode(java.util.UUID customerId, String statusCode);

    long countByCustomerIdAndStatusCodeIn(java.util.UUID customerId, java.util.Collection<String> statusCodes);

    @Query("SELECT r FROM Reservation r WHERE r.status.code = 'IN_PROGRESS' AND r.returnDate >= :from AND r.returnDate < :to")
    List<Reservation> findInProgressReturningBetween(@org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                                     @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    @Query("SELECT r FROM Reservation r WHERE r.status.code = 'IN_PROGRESS' AND r.returnDate < :now")
    List<Reservation> findInProgressOverdue(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @Query("SELECT r FROM Reservation r WHERE r.vehicle.id = :vehicleId AND r.blocksAvailability = true AND r.returnDate > :now ORDER BY r.pickupDate")
    List<Reservation> findUpcomingBlockingByVehicle(@Param("vehicleId") UUID vehicleId, @Param("now") java.time.Instant now);

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

    @Query("SELECT r FROM Reservation r " +
           "WHERE UPPER(r.status.code) = UPPER(:statusCode) " +
           "AND r.cashPaymentExpiresAt IS NOT NULL " +
           "AND r.cashPaymentExpiresAt < :now")
    List<Reservation> findExpiredPendingReservations(
            @Param("statusCode") String statusCode,
            @Param("now") Instant now
    );

    @Query("SELECT r FROM Reservation r " +
           "WHERE UPPER(r.status.code) = UPPER(:statusCode) " +
           "AND r.cashPaymentExpiresAt IS NOT NULL " +
           "AND r.cashPaymentExpiresAt > :now " +
           "AND r.cashPaymentExpiresAt <= :warningWindow")
    List<Reservation> findExpiringPendingReservations(
            @Param("statusCode") String statusCode,
            @Param("now") Instant now,
            @Param("warningWindow") Instant warningWindow
    );
}
