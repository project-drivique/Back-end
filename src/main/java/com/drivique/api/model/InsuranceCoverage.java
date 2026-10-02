package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "insurance_coverages", schema = "catalog")
public class InsuranceCoverage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2) private BigDecimal dailyRate;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    protected InsuranceCoverage() {}
    public InsuranceCoverage(String name, BigDecimal dailyRate, String description) { update(name, dailyRate, description); }
    public void update(String name, BigDecimal dailyRate, String description) { this.name = name; this.dailyRate = dailyRate; this.description = description; }
    public UUID getId() { return id; } public String getName() { return name; } public BigDecimal getDailyRate() { return dailyRate; } public String getDescription() { return description; } public boolean isActive() { return active; }
}
