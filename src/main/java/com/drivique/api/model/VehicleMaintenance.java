package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicle_maintenances", schema = "fleet")
public class VehicleMaintenance {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "vehicle_id", nullable = false) private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "maintenance_type_id", nullable = false) private MaintenanceType type;
    @Column(name = "scheduled_date", nullable = false) private LocalDate scheduledDate;
    @Column(name = "completed_date") private LocalDate completedDate;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal cost;
    @Column(columnDefinition = "text") private String description;

    protected VehicleMaintenance() {}
    public VehicleMaintenance(Vehicle vehicle, MaintenanceType type, LocalDate scheduledDate, BigDecimal cost, String description) {
        this.vehicle = vehicle; this.type = type; this.scheduledDate = scheduledDate; this.cost = cost; this.description = description;
    }
    public void complete(LocalDate date, BigDecimal finalCost) { this.completedDate = date; this.cost = finalCost; }
    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public MaintenanceType getType() { return type; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public BigDecimal getCost() { return cost; }
    public String getDescription() { return description; }
}
