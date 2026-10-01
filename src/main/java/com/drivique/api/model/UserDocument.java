package com.drivique.api.model;

import com.drivique.api.model.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_documents", schema = "iam")
public class UserDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "document_type_id", nullable = false)
    private DocumentType documentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "status_id", nullable = false)
    private DocumentStatus status;

    @Column(name = "document_number", length = 50)
    private String documentNumber;

    @Column(name = "front_url", nullable = false, length = 1000)
    private String frontUrl;

    @Column(name = "back_url", length = 1000)
    private String backUrl;

    @Column(name = "review_notes", length = 500)
    private String reviewNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserDocument() {}

    public UserDocument(User user, DocumentType documentType, DocumentStatus status, String documentNumber, String frontUrl, String backUrl) {
        this.user = user;
        this.documentType = documentType;
        this.status = status;
        this.documentNumber = documentNumber;
        this.frontUrl = frontUrl;
        this.backUrl = backUrl;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }

    public String getFrontUrl() { return frontUrl; }
    public void setFrontUrl(String frontUrl) { this.frontUrl = frontUrl; }

    public String getBackUrl() { return backUrl; }
    public void setBackUrl(String backUrl) { this.backUrl = backUrl; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
