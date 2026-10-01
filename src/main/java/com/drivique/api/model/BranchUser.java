package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branch_users", schema = "location")
@IdClass(BranchUserId.class)
public class BranchUser {
    @Id @Column(name = "user_id") private UUID userId;
    @Id @Column(name = "branch_id") private UUID branchId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", insertable = false, updatable = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id", insertable = false, updatable = false) private Branch branch;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt;

    protected BranchUser() {
    }

    public BranchUser(User user, Branch branch) {
        this.user = user;
        this.branch = branch;
        this.userId = user.getId();
        this.branchId = branch.getId();
        this.assignedAt = Instant.now();
    }

    public User getUser() { return user; }
    public Instant getAssignedAt() { return assignedAt; }
}
