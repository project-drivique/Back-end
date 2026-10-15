package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Conductor que entrega o recoge vehículos a domicilio para una sucursal. */
@Entity
@Table(name = "delivery_drivers", schema = "location")
public class DeliveryDriver {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(name = "document_type", nullable = false, length = 10)
    private String documentType = "CC";

    @Column(name = "document_number", nullable = false, length = 40)
    private String documentNumber;

    @Column(name = "license_number", length = 40)
    private String licenseNumber;

    @Column(name = "license_category", length = 5)
    private String licenseCategory;

    @Column(name = "license_expires_on")
    private LocalDate licenseExpiresOn;

    @Column(name = "vehicle_description", length = 120)
    private String vehicleDescription;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    protected DeliveryDriver() {
    }

    public DeliveryDriver(Branch branch) {
        this.branch = branch;
    }

    public UUID getId() { return id; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getLicenseCategory() { return licenseCategory; }
    public void setLicenseCategory(String licenseCategory) { this.licenseCategory = licenseCategory; }
    public LocalDate getLicenseExpiresOn() { return licenseExpiresOn; }
    public void setLicenseExpiresOn(LocalDate licenseExpiresOn) { this.licenseExpiresOn = licenseExpiresOn; }
    public String getVehicleDescription() { return vehicleDescription; }
    public void setVehicleDescription(String vehicleDescription) { this.vehicleDescription = vehicleDescription; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
