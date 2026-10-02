package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contract_statuses", schema = "contract")
public class ContractStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "is_final", nullable = false)
    private boolean isFinal = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ContractStatus() {}

    public ContractStatus(String code, String name, boolean isActive, boolean isFinal) {
        this.code = code;
        this.name = name;
        this.isActive = isActive;
        this.isFinal = isFinal;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return isActive; }
    public boolean isFinal() { return isFinal; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        isActive = active;
        this.updatedAt = Instant.now();
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
        this.updatedAt = Instant.now();
    }
}
