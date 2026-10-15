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
@Table(name = "incident_attachments", schema = "support")
public class IncidentAttachment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "incident_report_id", nullable = false) private IncidentReport incident;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "response_id") private IncidentResponse response;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_user_id", nullable = false) private User uploadedBy;
    @Column(name = "original_name", nullable = false, length = 255) private String originalName;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected IncidentAttachment() {
    }

    public IncidentAttachment(IncidentReport incident, IncidentResponse response, User uploadedBy, String originalName,
            String contentType, long sizeBytes, String storagePath) {
        this.incident = incident;
        this.response = response;
        this.uploadedBy = uploadedBy;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
    }

    public UUID getId() { return id; }
    public IncidentReport getIncident() { return incident; }
    public IncidentResponse getResponse() { return response; }
    public User getUploadedBy() { return uploadedBy; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getStoragePath() { return storagePath; }
    public Instant getCreatedAt() { return createdAt; }
}
