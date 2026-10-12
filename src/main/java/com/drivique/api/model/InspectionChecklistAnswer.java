package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_checklist_answers", schema = "contract", uniqueConstraints = @UniqueConstraint(name = "uq_inspection_checklist_answers_item", columnNames = {"inspection_id", "checklist_item_id"}))
public class InspectionChecklistAnswer {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inspection_id", nullable = false) private VehicleInspection inspection;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "checklist_item_id", nullable = false) private InspectionChecklistItem checklistItem;
    @Column(name = "is_compliant", nullable = false) private boolean compliant;
    @Column(columnDefinition = "text") private String observation;
    @Column(name = "evidence_photo_url", length = 1000) private String evidencePhotoUrl;
    @Column(name = "evidence_sha256", length = 64) private String evidenceSha256;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    protected InspectionChecklistAnswer() {}
    public InspectionChecklistAnswer(VehicleInspection inspection, InspectionChecklistItem checklistItem, boolean compliant, String observation, String evidencePhotoUrl, String evidenceSha256) { this.inspection = inspection; this.checklistItem = checklistItem; this.compliant = compliant; this.observation = observation; this.evidencePhotoUrl = evidencePhotoUrl; this.evidenceSha256 = evidenceSha256; }
    public UUID getId() { return id; } public VehicleInspection getInspection() { return inspection; } public InspectionChecklistItem getChecklistItem() { return checklistItem; } public boolean isCompliant() { return compliant; } public String getObservation() { return observation; } public String getEvidencePhotoUrl() { return evidencePhotoUrl; } public String getEvidenceSha256() { return evidenceSha256; }
}
