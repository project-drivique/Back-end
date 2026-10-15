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
@Table(name = "incident_responses", schema = "support")
public class IncidentResponse {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "incident_report_id", nullable = false) private IncidentReport incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_user_id", nullable = false) private User author;
    @Column(nullable = false, columnDefinition = "text") private String message;
    @Column(name = "attachment_url", length = 500) private String attachmentUrl;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    protected IncidentResponse() {
    }

    public IncidentResponse(IncidentReport incident, User author, String message, String attachmentUrl) {
        this.incident = incident;
        this.author = author;
        this.message = message;
        this.attachmentUrl = attachmentUrl;
    }

    public UUID getId() { return id; }
    public IncidentReport getIncident() { return incident; }
    public User getAuthor() { return author; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
