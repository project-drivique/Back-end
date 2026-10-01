package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "features", schema = "fleet")
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "feature_group", nullable = false, length = 30)
    private String featureGroup;

    @Column(length = 100)
    private String icon;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Feature() {}

    public Feature(String name, String featureGroup, String icon) {
        this.name = name;
        this.featureGroup = featureGroup;
        this.icon = icon;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getFeatureGroup() { return featureGroup; }
    public String getIcon() { return icon; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setFeatureGroup(String featureGroup) { this.featureGroup = featureGroup; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setActive(boolean active) { this.active = active; }
}
