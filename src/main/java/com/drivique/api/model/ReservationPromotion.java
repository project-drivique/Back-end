package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reservation_promotions", schema = "rental")
public class ReservationPromotion {

    @EmbeddedId
    private ReservationPromotionId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("promotionId")
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    @Column(name = "discount_applied", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ReservationPromotion() {}

    public ReservationPromotion(Reservation reservation, Promotion promotion, BigDecimal discountApplied) {
        this.reservation = reservation;
        this.promotion = promotion;
        this.id = new ReservationPromotionId(
                reservation != null ? reservation.getId() : null,
                promotion != null ? promotion.getId() : null
        );
        this.discountApplied = discountApplied;
        this.createdAt = Instant.now();
    }

    public ReservationPromotionId getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Promotion getPromotion() { return promotion; }
    public BigDecimal getDiscountApplied() { return discountApplied; }
    public Instant getCreatedAt() { return createdAt; }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public void setDiscountApplied(BigDecimal discountApplied) { this.discountApplied = discountApplied; }
}
