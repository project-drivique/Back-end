package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "maintenance_types", schema = "fleet")
public class MaintenanceType {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, unique = true, length = 80) private String name;
    @Column(name = "is_active", nullable = false) private boolean active = true;

    protected MaintenanceType() {}
    public MaintenanceType(String code, String name) { this.code = code; this.name = name; }
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
