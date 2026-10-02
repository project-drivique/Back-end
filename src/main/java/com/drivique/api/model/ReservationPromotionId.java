package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationPromotionId implements Serializable {

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "promotion_id")
    private UUID promotionId;

    public ReservationPromotionId() {}

    public ReservationPromotionId(UUID reservationId, UUID promotionId) {
        this.reservationId = reservationId;
        this.promotionId = promotionId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getPromotionId() { return promotionId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationPromotionId that)) return false;
        return Objects.equals(reservationId, that.reservationId) && Objects.equals(promotionId, that.promotionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, promotionId);
    }
}
