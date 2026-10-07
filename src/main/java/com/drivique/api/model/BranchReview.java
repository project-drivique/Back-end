package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "branch_reviews",
    schema = "rental",
    uniqueConstraints = @UniqueConstraint(name = "uq_branch_reviews_reservation_branch", columnNames = {"reservation_id", "branch_id"})
)
public class BranchReview {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false)
    private short rating;

    @Column(length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected BranchReview() {}

    public BranchReview(Branch b, User u, Reservation r, short rating, String comment) {
        branch = b;
        user = u;
        reservation = r;
        this.rating = rating;
        this.comment = comment;
    }

    public UUID getId() { return id; }
    public Branch getBranch() { return branch; }
    public User getUser() { return user; }
    public Reservation getReservation() { return reservation; }
    public short getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
