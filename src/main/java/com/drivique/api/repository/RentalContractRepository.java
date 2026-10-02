package com.drivique.api.repository;

import com.drivique.api.model.RentalContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    Optional<RentalContract> findByContractNumber(String contractNumber);

    Optional<RentalContract> findByReservationId(UUID reservationId);

    boolean existsByReservationId(UUID reservationId);

    long countByContractNumberStartingWith(String prefix);

    List<RentalContract> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
}
