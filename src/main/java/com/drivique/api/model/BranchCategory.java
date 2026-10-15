package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_categories", schema = "location")
@IdClass(BranchCategoryId.class)
public class BranchCategory {
    @Id @Column(name = "branch_id") private UUID branchId;
    @Id @Column(name = "category_id") private UUID categoryId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected BranchCategory() {
    }

    public BranchCategory(UUID branchId, UUID categoryId) {
        this.branchId = branchId;
        this.categoryId = categoryId;
    }

    public UUID getBranchId() { return branchId; }
    public UUID getCategoryId() { return categoryId; }
}
