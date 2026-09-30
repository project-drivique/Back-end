package com.drivique.api.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "roles", schema = "iam")
public class Role {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Role() {}

    public Role(UUID id, String code, String name, String description, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.active = active;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
