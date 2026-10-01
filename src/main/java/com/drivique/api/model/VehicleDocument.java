package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vehicle_documents", schema = "fleet")
public class VehicleDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "document_type", nullable = false, length = 30)
    private String documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "issued_at")
    private LocalDate issuedAt;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected VehicleDocument() {}

    public VehicleDocument(
            Vehicle vehicle,
            String documentType,
            String documentNumber,
            String fileUrl,
            LocalDate issuedAt,
            LocalDate expiresAt
    ) {
        this.vehicle = vehicle;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.fileUrl = fileUrl;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public String getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public String getFileUrl() { return fileUrl; }
    public LocalDate getIssuedAt() { return issuedAt; }
    public LocalDate getExpiresAt() { return expiresAt; }
    public boolean isActive() { return active; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }
    public void setIssuedAt(LocalDate issuedAt) { this.issuedAt = issuedAt; }
    public void setExpiresAt(LocalDate expiresAt) { this.expiresAt = expiresAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
