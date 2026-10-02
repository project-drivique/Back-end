package com.drivique.api.repository;

import com.drivique.api.model.ReservationPromotion;
import com.drivique.api.model.ReservationPromotionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationPromotionRepository extends JpaRepository<ReservationPromotion, ReservationPromotionId> {
    List<ReservationPromotion> findByReservationId(UUID reservationId);
}
