package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "reservations",
    schema = "rental",
    indexes = {
        @Index(name = "idx_reservations_status", columnList = "status_id")
    }
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private ReservationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurance_coverage_id", nullable = false)
    private InsuranceCoverage insuranceCoverage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mileage_plan_id", nullable = false)
    private MileagePlan mileagePlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_payment_branch_id")
    private Branch cashPaymentBranch;

    @Column(name = "pickup_date", nullable = false)
    private Instant pickupDate;

    @Column(name = "return_date", nullable = false)
    private Instant returnDate;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "total_estimated", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEstimated;

    @Column(name = "cash_payment_code", unique = true, length = 30)
    private String cashPaymentCode;

    @Column(name = "cash_payment_expires_at")
    private Instant cashPaymentExpiresAt;

    @Column(name = "blocks_availability", nullable = false)
    private boolean blocksAvailability = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "reservation")
    private List<ReservationAdditionalService> additionalServices = new ArrayList<>();

    @OneToMany(mappedBy = "reservation")
    private List<ReservationPromotion> promotions = new ArrayList<>();

    @OneToMany(mappedBy = "reservation")
    private List<ReservationDeliveryPoint> deliveryPoints = new ArrayList<>();

    protected Reservation() {}

    public Reservation(
            String code,
            User customer,
            Vehicle vehicle,
            ReservationStatus status,
            InsuranceCoverage insuranceCoverage,
            MileagePlan mileagePlan,
            Branch cashPaymentBranch,
            Instant pickupDate,
            Instant returnDate,
            BigDecimal dailyRate,
            BigDecimal totalEstimated,
            String cashPaymentCode,
            Instant cashPaymentExpiresAt,
            boolean blocksAvailability
    ) {
        this.code = code;
        this.customer = customer;
        this.vehicle = vehicle;
        this.status = status;
        this.insuranceCoverage = insuranceCoverage;
        this.mileagePlan = mileagePlan;
        this.cashPaymentBranch = cashPaymentBranch;
        this.pickupDate = pickupDate;
        this.returnDate = returnDate;
        this.dailyRate = dailyRate;
        this.totalEstimated = totalEstimated;
        this.cashPaymentCode = cashPaymentCode;
        this.cashPaymentExpiresAt = cashPaymentExpiresAt;
        this.blocksAvailability = blocksAvailability;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public User getCustomer() { return customer; }
    public Vehicle getVehicle() { return vehicle; }
    public ReservationStatus getStatus() { return status; }
    public InsuranceCoverage getInsuranceCoverage() { return insuranceCoverage; }
    public MileagePlan getMileagePlan() { return mileagePlan; }
    public Branch getCashPaymentBranch() { return cashPaymentBranch; }
    public Instant getPickupDate() { return pickupDate; }
    public Instant getReturnDate() { return returnDate; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public BigDecimal getTotalEstimated() { return totalEstimated; }
    public String getCashPaymentCode() { return cashPaymentCode; }
    public Instant getCashPaymentExpiresAt() { return cashPaymentExpiresAt; }
    public boolean isBlocksAvailability() { return blocksAvailability; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ReservationAdditionalService> getAdditionalServices() { return additionalServices; }
    public List<ReservationPromotion> getPromotions() { return promotions; }
    public List<ReservationDeliveryPoint> getDeliveryPoints() { return deliveryPoints; }

    public void setCode(String code) {
        this.code = code;
        this.updatedAt = Instant.now();
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
        if (status != null) {
            this.blocksAvailability = status.isBlocksAvailability();
        }
        this.updatedAt = Instant.now();
    }

    public void setBlocksAvailability(boolean blocksAvailability) {
        this.blocksAvailability = blocksAvailability;
        this.updatedAt = Instant.now();
    }

    public void setPickupDate(Instant pickupDate) {
        this.pickupDate = pickupDate;
        this.updatedAt = Instant.now();
    }

    public void setReturnDate(Instant returnDate) {
        this.returnDate = returnDate;
        this.updatedAt = Instant.now();
    }

    public void setInsuranceCoverage(InsuranceCoverage insuranceCoverage) {
        this.insuranceCoverage = insuranceCoverage;
        this.updatedAt = Instant.now();
    }

    public void setMileagePlan(MileagePlan mileagePlan) {
        this.mileagePlan = mileagePlan;
        this.updatedAt = Instant.now();
    }

    public void setTotalEstimated(BigDecimal totalEstimated) {
        this.totalEstimated = totalEstimated;
        this.updatedAt = Instant.now();
    }

    public void setCashPaymentCode(String cashPaymentCode) {
        this.cashPaymentCode = cashPaymentCode;
        this.updatedAt = Instant.now();
    }

    public void setCashPaymentExpiresAt(Instant cashPaymentExpiresAt) {
        this.cashPaymentExpiresAt = cashPaymentExpiresAt;
        this.updatedAt = Instant.now();
    }

    public void setCashPaymentBranch(Branch cashPaymentBranch) {
        this.cashPaymentBranch = cashPaymentBranch;
        this.updatedAt = Instant.now();
    }

    public void addAdditionalService(ReservationAdditionalService service) {
        if (service != null) {
            this.additionalServices.add(service);
        }
    }

    public void addPromotion(ReservationPromotion promo) {
        if (promo != null) {
            this.promotions.add(promo);
        }
    }

    public void addDeliveryPoint(ReservationDeliveryPoint point) {
        if (point != null) {
            this.deliveryPoints.add(point);
        }
    }
}
