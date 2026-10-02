package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "reservation_additional_services", schema = "rental")
public class ReservationAdditionalService {

    @EmbeddedId
    private ReservationAdditionalServiceId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("additionalServiceId")
    @JoinColumn(name = "additional_service_id", nullable = false)
    private AdditionalService additionalService;

    @Column(nullable = false)
    private short quantity = 1;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ReservationAdditionalService() {}

    public ReservationAdditionalService(Reservation reservation, AdditionalService additionalService, short quantity, BigDecimal dailyRate) {
        this.reservation = reservation;
        this.additionalService = additionalService;
        this.id = new ReservationAdditionalServiceId(
                reservation != null ? reservation.getId() : null,
                additionalService != null ? additionalService.getId() : null
        );
        this.quantity = quantity;
        this.dailyRate = dailyRate;
        this.createdAt = Instant.now();
    }

    public ReservationAdditionalServiceId getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public AdditionalService getAdditionalService() { return additionalService; }
    public short getQuantity() { return quantity; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public Instant getCreatedAt() { return createdAt; }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
        if (this.id == null) {
            this.id = new ReservationAdditionalServiceId();
        }
        if (reservation != null && reservation.getId() != null) {
            this.id = new ReservationAdditionalServiceId(reservation.getId(), this.additionalService != null ? this.additionalService.getId() : null);
        }
    }

    public void setQuantity(short quantity) { this.quantity = quantity; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
}
