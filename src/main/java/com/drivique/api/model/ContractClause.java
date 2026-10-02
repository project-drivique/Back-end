package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "contract_clauses",
        schema = "contract",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_contract_clauses_version_sort_order", columnNames = {"version", "sort_order"})
        }
)
public class ContractClause {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String version;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ContractClause() {}

    public ContractClause(String version, short sortOrder, String title, String content, boolean isActive) {
        this.version = version;
        this.sortOrder = sortOrder;
        this.title = title;
        this.content = content;
        this.isActive = isActive;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public short getSortOrder() { return sortOrder; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public boolean isActive() { return isActive; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSortOrder(short sortOrder) {
        this.sortOrder = sortOrder;
        this.updatedAt = Instant.now();
    }

    public void setTitle(String title) {
        this.title = title;
        this.updatedAt = Instant.now();
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        isActive = active;
        this.updatedAt = Instant.now();
    }
}
