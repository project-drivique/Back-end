package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "branches", schema = "location")
public class Branch {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(nullable = false, length = 255) private String address;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "city_id", nullable = false) private City city;
    @Column(nullable = false, length = 30) private String phone;
    @Column(name = "opening_time", nullable = false) private LocalTime openingTime;
    @Column(name = "closing_time", nullable = false) private LocalTime closingTime;
    @Column(name = "allows_cash_payment", nullable = false) private boolean allowsCashPayment;
    @Column(name = "is_active", nullable = false) private boolean active;
    @Column(name = "vehicle_capacity", nullable = false) private int vehicleCapacity = 20;
    @Column(name = "pickup_instructions", length = 500) private String pickupInstructions;
    @Column(name = "latitude", precision = 9, scale = 6) private java.math.BigDecimal latitude;
    @Column(name = "longitude", precision = 9, scale = 6) private java.math.BigDecimal longitude;
    protected Branch() {}
    public Branch(String name, String address, City city, String phone, LocalTime openingTime, LocalTime closingTime, boolean allowsCashPayment) {
        update(name, address, city, phone, openingTime, closingTime, allowsCashPayment); active = true;
    }
    public void update(String name, String address, City city, String phone, LocalTime openingTime, LocalTime closingTime, boolean allowsCashPayment) {
        this.name = name; this.address = address; this.city = city; this.phone = phone; this.openingTime = openingTime; this.closingTime = closingTime; this.allowsCashPayment = allowsCashPayment;
    }
    public void toggleStatus() { active = !active; }
    public String getPickupInstructions() { return pickupInstructions; }
    public java.math.BigDecimal getLatitude() { return latitude; }
    public java.math.BigDecimal getLongitude() { return longitude; }
    public void updateProfile(String address, String phone, String pickupInstructions, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        this.address = address; this.phone = phone; this.pickupInstructions = pickupInstructions; this.latitude = latitude; this.longitude = longitude;
    }
    public int getVehicleCapacity() { return vehicleCapacity; }
    public void setVehicleCapacity(int vehicleCapacity) { this.vehicleCapacity = vehicleCapacity; }
    public UUID getId() { return id; } public String getName() { return name; } public String getAddress() { return address; }
    public City getCity() { return city; } public String getPhone() { return phone; } public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; } public boolean allowsCashPayment() { return allowsCashPayment; } public boolean isActive() { return active; }
}
