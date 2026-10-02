package com.drivique.api.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name = "mileage_plans", schema = "catalog")
public class MileagePlan {
 @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
 @Column(nullable = false, unique = true, length = 120) private String name;
 @Column(name = "included_km") private Integer includedKm;
 @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2) private BigDecimal dailyRate;
 @Column(name = "extra_km_rate", nullable = false, precision = 12, scale = 2) private BigDecimal extraKmRate;
 @Column(name = "is_active", nullable = false) private boolean active = true;
 protected MileagePlan() {}
 public MileagePlan(String name, Integer includedKm, BigDecimal dailyRate, BigDecimal extraKmRate) { update(name, includedKm, dailyRate, extraKmRate); }
 public void update(String name, Integer includedKm, BigDecimal dailyRate, BigDecimal extraKmRate) { this.name = name; this.includedKm = includedKm; this.dailyRate = dailyRate; this.extraKmRate = extraKmRate; }
 public UUID getId(){return id;} public String getName(){return name;} public Integer getIncludedKm(){return includedKm;} public BigDecimal getDailyRate(){return dailyRate;} public BigDecimal getExtraKmRate(){return extraKmRate;} public boolean isActive(){return active;}
}
