package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_statuses", schema = "rental")
public class ReservationStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "blocks_availability", nullable = false)
    private boolean blocksAvailability = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ReservationStatus() {}

    public ReservationStatus(String code, String name, boolean blocksAvailability) {
        this.code = code;
        this.name = name;
        this.blocksAvailability = blocksAvailability;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isBlocksAvailability() { return blocksAvailability; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public void setBlocksAvailability(boolean blocksAvailability) {
        this.blocksAvailability = blocksAvailability;
        this.updatedAt = Instant.now();
    }
}
