package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotions", schema = "catalog")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String conditions;

    @Column(name = "offer_type", nullable = false, length = 20)
    private String offerType;

    @Column(name = "discount_type", nullable = false, length = 20)
    private String discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "minimum_rental_days", nullable = false)
    private int minimumRentalDays = 1;

    @Column(name = "minimum_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumAmount = BigDecimal.ZERO;

    @Column(name = "max_uses_limit")
    private Integer maxUsesLimit;

    @Column(name = "current_uses_count", nullable = false)
    private int currentUsesCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_category_id")
    private VehicleCategory vehicleCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, length = 20)
    private String audience = "ALL";

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    protected Promotion() {
    }

    public Promotion(String code, String offerType, String discountType, BigDecimal discountValue, Instant startsAt,
                     Instant endsAt, int minimumRentalDays, Integer maxUsesLimit, int currentUsesCount) {
        this.code = code;
        this.offerType = offerType;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.minimumRentalDays = minimumRentalDays;
        this.maxUsesLimit = maxUsesLimit;
        this.currentUsesCount = currentUsesCount;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getConditions() { return conditions; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public String getOfferType() { return offerType; }
    public void setOfferType(String offerType) { this.offerType = offerType; }
    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public int getMinimumRentalDays() { return minimumRentalDays; }
    public void setMinimumRentalDays(int minimumRentalDays) { this.minimumRentalDays = minimumRentalDays; }
    public BigDecimal getMinimumAmount() { return minimumAmount; }
    public void setMinimumAmount(BigDecimal minimumAmount) { this.minimumAmount = minimumAmount; }
    public Integer getMaxUsesLimit() { return maxUsesLimit; }
    public void setMaxUsesLimit(Integer maxUsesLimit) { this.maxUsesLimit = maxUsesLimit; }
    public int getCurrentUsesCount() { return currentUsesCount; }
    public void setCurrentUsesCount(int currentUsesCount) { this.currentUsesCount = currentUsesCount; }
    public void incrementUsesCount() { this.currentUsesCount++; }
    public VehicleCategory getVehicleCategory() { return vehicleCategory; }
    public void setVehicleCategory(VehicleCategory vehicleCategory) { this.vehicleCategory = vehicleCategory; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public boolean isActive() { return active; }
    @Column(name = "notification_sent_at") private Instant notificationSentAt;
    public Instant getNotificationSentAt() { return notificationSentAt; }
    public void markNotified() { this.notificationSentAt = Instant.now(); }
    public void setActive(boolean active) { this.active = active; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
