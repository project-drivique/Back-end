package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_images", schema = "fleet")
public class VehicleImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false, length = 1000)
    private String url;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected VehicleImage() {}

    public VehicleImage(Vehicle vehicle, String url, boolean primary, short sortOrder) {
        this.vehicle = vehicle;
        this.url = url;
        this.primary = primary;
        this.sortOrder = sortOrder;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public String getUrl() { return url; }
    public boolean isPrimary() { return primary; }
    public short getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public void setUrl(String url) { this.url = url; }
    public void setPrimary(boolean primary) { this.primary = primary; }
    public void setSortOrder(short sortOrder) { this.sortOrder = sortOrder; }
}
