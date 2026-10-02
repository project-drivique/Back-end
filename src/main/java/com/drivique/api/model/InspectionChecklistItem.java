package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklist_items", schema = "contract")
public class InspectionChecklistItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true, length = 120) private String name;
    @Column(length = 255) private String description;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    protected InspectionChecklistItem() {}
    public InspectionChecklistItem(String name, String description) { this.name = name; this.description = description; }
    public UUID getId() { return id; } public String getName() { return name; } public String getDescription() { return description; } public boolean isActive() { return active; }
}
