package com.drivique.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vehicle_inspections", schema = "contract", uniqueConstraints = @UniqueConstraint(name = "uq_vehicle_inspections_contract_type", columnNames = {"contract_id", "inspection_type"}))
public class VehicleInspection {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "contract_id", nullable = false) private RentalContract contract;
    @Column(name = "inspection_type", nullable = false, length = 20) private String inspectionType;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inspector_user_id", nullable = false) private User inspector;
    @Column(nullable = false) private int mileage;
    @Column(name = "fuel_level_percent", nullable = false, precision = 5, scale = 2) private BigDecimal fuelLevelPercent;
    @Column(columnDefinition = "text") private String observations;
    @OneToMany(mappedBy = "inspection", cascade = CascadeType.ALL, orphanRemoval = true) private List<InspectionChecklistAnswer> answers = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    protected VehicleInspection() {}
    public VehicleInspection(RentalContract contract, String inspectionType, User inspector, int mileage, BigDecimal fuelLevelPercent, String observations) { this.contract = contract; this.inspectionType = inspectionType; this.inspector = inspector; this.mileage = mileage; this.fuelLevelPercent = fuelLevelPercent; this.observations = observations; }
    public UUID getId() { return id; } public RentalContract getContract() { return contract; } public String getInspectionType() { return inspectionType; } public User getInspector() { return inspector; } public int getMileage() { return mileage; } public BigDecimal getFuelLevelPercent() { return fuelLevelPercent; } public String getObservations() { return observations; } public List<InspectionChecklistAnswer> getAnswers() { return answers; } public Instant getCreatedAt() { return createdAt; }
    public void addAnswer(InspectionChecklistAnswer answer) { answers.add(answer); }
}
