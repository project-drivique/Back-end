package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicles", schema = "fleet")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String plate;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private VehicleBrand brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private VehicleCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transmission_type_id", nullable = false)
    private TransmissionType transmissionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fuel_type_id", nullable = false)
    private FuelType fuelType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private VehicleStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_branch_id", nullable = false)
    private Branch currentBranch;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false)
    private short year;

    @Column(length = 50)
    private String color;

    @Column(name = "passenger_capacity", nullable = false)
    private short passengerCapacity;

    @Column(name = "doors_count")
    private Short doorsCount;

    @Column(name = "trunk_capacity_liters")
    private Integer trunkCapacityLiters;

    @Column(nullable = false)
    private int mileage;

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "main_image_url", length = 2048)
    private String mainImageUrl;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Vehicle() {}

    public Vehicle(
            String plate,
            String vin,
            VehicleBrand brand,
            VehicleCategory category,
            TransmissionType transmissionType,
            FuelType fuelType,
            VehicleStatus status,
            Branch currentBranch,
            String model,
            short year,
            String color,
            short passengerCapacity,
            Short doorsCount,
            Integer trunkCapacityLiters,
            int mileage,
            BigDecimal dailyRate,
            String mainImageUrl,
            boolean featured
    ) {
        this.plate = plate;
        this.vin = vin;
        this.brand = brand;
        this.category = category;
        this.transmissionType = transmissionType;
        this.fuelType = fuelType;
        this.status = status;
        this.currentBranch = currentBranch;
        this.model = model;
        this.year = year;
        this.color = color;
        this.passengerCapacity = passengerCapacity;
        this.doorsCount = doorsCount;
        this.trunkCapacityLiters = trunkCapacityLiters;
        this.mileage = mileage;
        this.dailyRate = dailyRate;
        this.mainImageUrl = mainImageUrl;
        this.featured = featured;
        this.active = true;
    }

    public UUID getId() { return id; }
    public String getPlate() { return plate; }
    public String getVin() { return vin; }
    public VehicleBrand getBrand() { return brand; }
    public VehicleCategory getCategory() { return category; }
    public TransmissionType getTransmissionType() { return transmissionType; }
    public FuelType getFuelType() { return fuelType; }
    public VehicleStatus getStatus() { return status; }
    public Branch getCurrentBranch() { return currentBranch; }
    public String getModel() { return model; }
    public short getYear() { return year; }
    public String getColor() { return color; }
    public short getPassengerCapacity() { return passengerCapacity; }
    public Short getDoorsCount() { return doorsCount; }
    public Integer getTrunkCapacityLiters() { return trunkCapacityLiters; }
    public int getMileage() { return mileage; }
    public BigDecimal getDailyRate() { return dailyRate; }
    public String getMainImageUrl() { return mainImageUrl; }
    public boolean isFeatured() { return featured; }
    public boolean isActive() { return active; }

    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
    public void setMileage(int mileage) { this.mileage = mileage; }
    public void setCurrentBranch(Branch currentBranch) { this.currentBranch = currentBranch; }
    public void setStatus(VehicleStatus status) { this.status = status; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void toggleActive() { this.active = !this.active; }
}
