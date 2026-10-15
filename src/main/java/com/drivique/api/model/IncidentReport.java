package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_reports", schema = "support")
public class IncidentReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "incident_code", nullable = false, unique = true, length = 30) private String incidentCode;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vehicle_id") private Vehicle vehicle;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id") private Reservation reservation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reported_by_user_id", nullable = false) private User reporter;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "branch_id") private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_to_user_id") private User assignedTo;
    @Column(nullable = false, length = 20) private String category = "OTHER";
    @Column(nullable = false, length = 20) private String status = "OPEN";
    @Column(nullable = false, length = 20) private String priority = "MEDIUM";
    @Column(nullable = false, length = 150) private String subject;
    @Column(nullable = false, columnDefinition = "text") private String description;
    @Column(name = "sla_due_at") private Instant slaDueAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected IncidentReport() {
    }

    public IncidentReport(String code, Vehicle vehicle, Reservation reservation, User reporter, String priority, String subject, String description) {
        this.incidentCode = code;
        this.vehicle = vehicle;
        this.reservation = reservation;
        this.reporter = reporter;
        this.priority = priority;
        this.subject = subject;
        this.description = description;
    }

    public void classify(String category, String priority, Branch branch, Instant slaDueAt) {
        this.category = category;
        this.priority = priority;
        this.branch = branch;
        this.slaDueAt = slaDueAt;
    }

    public void assignTo(User operator) {
        this.assignedTo = operator;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(String newStatus) {
        this.status = newStatus;
        Instant now = Instant.now();
        this.updatedAt = now;
        if ("RESOLVED".equals(newStatus)) this.resolvedAt = now;
        if ("CLOSED".equals(newStatus)) this.closedAt = now;
        if ("IN_REVIEW".equals(newStatus) || "OPEN".equals(newStatus)) {
            this.resolvedAt = null;
            this.closedAt = null;
        }
    }

    public void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getIncidentCode() { return incidentCode; }
    public Vehicle getVehicle() { return vehicle; }
    public Reservation getReservation() { return reservation; }
    public User getReporter() { return reporter; }
    public Branch getBranch() { return branch; }
    public User getAssignedTo() { return assignedTo; }
    public String getCategory() { return category; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getClosedAt() { return closedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Compatibilidad con el flujo anterior de cambio de estado directo. */
    public void setStatus(String value) { changeStatus(value); }
}
