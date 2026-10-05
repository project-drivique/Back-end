package com.drivique.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "rental_contracts",
        schema = "contract",
        indexes = {
                @Index(name = "idx_rental_contracts_reservation", columnList = "reservation_id"),
                @Index(name = "idx_rental_contracts_customer", columnList = "customer_id"),
                @Index(name = "idx_rental_contracts_vehicle", columnList = "vehicle_id"),
                @Index(name = "idx_rental_contracts_status", columnList = "status_id")
        }
)
public class RentalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_number", nullable = false, unique = true, length = 30)
    private String contractNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private ContractStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_branch_id", nullable = false)
    private Branch pickupBranch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_branch_id", nullable = false)
    private Branch returnBranch;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Column(name = "base_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "security_deposit", nullable = false, precision = 12, scale = 2)
    private BigDecimal securityDeposit = BigDecimal.ZERO;

    @Column(name = "signature_url", length = 1000)
    private String signatureUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signature_stroke_data", columnDefinition = "jsonb")
    private String signatureStrokeData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_city_id")
    private City signedCity;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "pdf_url", length = 1000)
    private String pdfUrl;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "contract_clause_assignments",
            schema = "contract",
            joinColumns = @JoinColumn(name = "contract_id"),
            inverseJoinColumns = @JoinColumn(name = "clause_id")
    )
    private List<ContractClause> clauses = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected RentalContract() {}

    public RentalContract(
            String contractNumber,
            Reservation reservation,
            User customer,
            Vehicle vehicle,
            ContractStatus status,
            Branch pickupBranch,
            Branch returnBranch,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            BigDecimal baseAmount,
            BigDecimal securityDeposit,
            List<ContractClause> clauses
    ) {
        this.contractNumber = contractNumber;
        this.reservation = reservation;
        this.customer = customer;
        this.vehicle = vehicle;
        this.status = status;
        this.pickupBranch = pickupBranch;
        this.returnBranch = returnBranch;
        this.scheduledStartAt = scheduledStartAt;
        this.scheduledEndAt = scheduledEndAt;
        this.baseAmount = baseAmount;
        this.securityDeposit = securityDeposit != null ? securityDeposit : BigDecimal.ZERO;
        if (clauses != null) {
            this.clauses.addAll(clauses);
        }
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getContractNumber() { return contractNumber; }
    public Reservation getReservation() { return reservation; }
    public User getCustomer() { return customer; }
    public Vehicle getVehicle() { return vehicle; }
    public ContractStatus getStatus() { return status; }
    public Branch getPickupBranch() { return pickupBranch; }
    public Branch getReturnBranch() { return returnBranch; }
    public Instant getScheduledStartAt() { return scheduledStartAt; }
    public Instant getScheduledEndAt() { return scheduledEndAt; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getSecurityDeposit() { return securityDeposit; }
    public String getSignatureUrl() { return signatureUrl; }
    public String getSignatureStrokeData() { return signatureStrokeData; }
    public City getSignedCity() { return signedCity; }
    public Instant getSignedAt() { return signedAt; }
    public String getPdfUrl() { return pdfUrl; }
    public List<ContractClause> getClauses() { return clauses; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(ContractStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setSignatureUrl(String signatureUrl) {
        this.signatureUrl = signatureUrl;
        this.updatedAt = Instant.now();
    }

    public void setSignatureStrokeData(String signatureStrokeData) {
        this.signatureStrokeData = signatureStrokeData;
        this.updatedAt = Instant.now();
    }

    public void setSignedCity(City signedCity) {
        this.signedCity = signedCity;
        this.updatedAt = Instant.now();
    }

    public void setSignedAt(Instant signedAt) {
        this.signedAt = signedAt;
        this.updatedAt = Instant.now();
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
        this.updatedAt = Instant.now();
    }

    public void addClause(ContractClause clause) {
        if (clause != null) {
            this.clauses.add(clause);
        }
    }
}


