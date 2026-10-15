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
@Table(name = "incident_status_history", schema = "support")
public class IncidentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "incident_report_id", nullable = false) private IncidentReport incident;
    @Column(name = "from_status", length = 20) private String fromStatus;
    @Column(name = "to_status", nullable = false, length = 20) private String toStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by_user_id", nullable = false) private User changedBy;
    @Column(columnDefinition = "text") private String note;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected IncidentStatusHistory() {
    }

    public IncidentStatusHistory(IncidentReport incident, String fromStatus, String toStatus, User changedBy, String note) {
        this.incident = incident;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
    }

    public UUID getId() { return id; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public User getChangedBy() { return changedBy; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
