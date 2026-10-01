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
    protected Branch() {}
    public Branch(String name, String address, City city, String phone, LocalTime openingTime, LocalTime closingTime, boolean allowsCashPayment) {
        update(name, address, city, phone, openingTime, closingTime, allowsCashPayment); active = true;
    }
    public void update(String name, String address, City city, String phone, LocalTime openingTime, LocalTime closingTime, boolean allowsCashPayment) {
        this.name = name; this.address = address; this.city = city; this.phone = phone; this.openingTime = openingTime; this.closingTime = closingTime; this.allowsCashPayment = allowsCashPayment;
    }
    public void toggleStatus() { active = !active; }
    public UUID getId() { return id; } public String getName() { return name; } public String getAddress() { return address; }
    public City getCity() { return city; } public String getPhone() { return phone; } public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; } public boolean allowsCashPayment() { return allowsCashPayment; } public boolean isActive() { return active; }
}
