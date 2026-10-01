package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicle_categories", schema = "fleet")
public class VehicleCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "base_daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseDailyRate;

    @Column(name = "security_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected VehicleCategory() {}

    public VehicleCategory(String name, BigDecimal baseDailyRate, BigDecimal securityDeposit) {
        update(name, baseDailyRate, securityDeposit);
        this.active = true;
    }

    public void update(String name, BigDecimal baseDailyRate, BigDecimal securityDeposit) {
        this.name = name;
        this.baseDailyRate = baseDailyRate;
        this.securityDeposit = securityDeposit;
    }

    public void toggleStatus() {
        this.active = !this.active;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getBaseDailyRate() { return baseDailyRate; }
    public BigDecimal getSecurityDeposit() { return securityDeposit; }
    public boolean isActive() { return active; }
}
