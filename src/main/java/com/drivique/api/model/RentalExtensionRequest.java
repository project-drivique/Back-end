package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rental_extension_requests",
        schema = "rental",
        indexes = {
                @Index(name = "idx_rental_extension_requests_reservation", columnList = "reservation_id"),
                @Index(name = "idx_rental_extension_requests_status", columnList = "status"),
                @Index(name = "idx_rental_extension_requests_reviewer", columnList = "reviewed_by")
        }
)
public class RentalExtensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "requested_return_date", nullable = false)
    private Instant requestedReturnDate;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "additional_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal additionalAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RentalExtensionRequest() {}

    public RentalExtensionRequest(
            Reservation reservation,
            Instant requestedReturnDate,
            BigDecimal additionalAmount
    ) {
        this.reservation = reservation;
        this.requestedReturnDate = requestedReturnDate;
        this.additionalAmount = additionalAmount != null ? additionalAmount : BigDecimal.ZERO;
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Instant getRequestedReturnDate() { return requestedReturnDate; }
    public String getStatus() { return status; }
    public BigDecimal getAdditionalAmount() { return additionalAmount; }
    public User getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRequestedReturnDate(Instant requestedReturnDate) {
        this.requestedReturnDate = requestedReturnDate;
        this.updatedAt = Instant.now();
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setAdditionalAmount(BigDecimal additionalAmount) {
        this.additionalAmount = additionalAmount;
        this.updatedAt = Instant.now();
    }

    public void setReviewedBy(User reviewedBy) {
        this.reviewedBy = reviewedBy;
        this.updatedAt = Instant.now();
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
        this.updatedAt = Instant.now();
    }
}
