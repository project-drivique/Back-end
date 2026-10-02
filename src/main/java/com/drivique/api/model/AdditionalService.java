package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "additional_services", schema = "catalog")
public class AdditionalService {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2) private BigDecimal dailyRate;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    protected AdditionalService() {}
    public AdditionalService(String name, BigDecimal dailyRate) { update(name, dailyRate); }
    public void update(String name, BigDecimal dailyRate) { this.name = name; this.dailyRate = dailyRate; }
    public UUID getId() { return id; } public String getName() { return name; } public BigDecimal getDailyRate() { return dailyRate; } public boolean isActive() { return active; }
}
