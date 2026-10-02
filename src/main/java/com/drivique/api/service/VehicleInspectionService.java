package com.drivique.api.service;

import com.drivique.api.dto.*;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VehicleInspectionService {
    private final RentalContractRepository contracts;
    private final VehicleInspectionRepository inspections;
    private final InspectionChecklistItemRepository checklistItems;
    private final UserRepository users;
    private final FileStorageService storage;

    public VehicleInspectionService(RentalContractRepository contracts, VehicleInspectionRepository inspections, InspectionChecklistItemRepository checklistItems, UserRepository users, FileStorageService storage) {
        this.contracts = contracts; this.inspections = inspections; this.checklistItems = checklistItems; this.users = users; this.storage = storage;
    }

    @Transactional
    public VehicleInspectionResponseDTO register(UUID contractId, VehicleInspectionRequestDTO request, List<MultipartFile> evidencePhotos, String email) {
        String type = request.inspectionType().trim().toUpperCase(Locale.ROOT);
        RentalContract contract = contracts.findById(contractId).orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado"));
        User inspector = users.findByEmailIgnoreCaseAndDeletedAtIsNull(email).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (inspections.existsByContractIdAndInspectionType(contractId, type)) throw new ConflictException("Ya existe una inspección " + type + " para este contrato.");

        VehicleInspection checkIn = null;
        if ("CHECK_OUT".equals(type)) {
            checkIn = inspections.findByContractIdAndInspectionType(contractId, "CHECK_IN")
                    .orElseThrow(() -> new ConflictException("Debe registrar el CHECK_IN antes del CHECK_OUT."));
            if (request.mileage() < checkIn.getMileage()) throw new IllegalArgumentException("El kilometraje de devolución no puede ser menor al registrado en la entrega.");
        }
        if (request.mileage() < contract.getVehicle().getMileage()) throw new IllegalArgumentException("El kilometraje no puede ser menor al kilometraje actual del vehículo.");

        VehicleInspection inspection = new VehicleInspection(contract, type, inspector, request.mileage(), request.fuelLevelPercent(), blankToNull(request.observations()));
        Set<UUID> answeredItems = new HashSet<>();
        List<MultipartFile> photos = evidencePhotos == null ? List.of() : evidencePhotos;
        for (InspectionChecklistAnswerRequestDTO answerRequest : request.answers()) {
            if (!answeredItems.add(answerRequest.checklistItemId())) throw new IllegalArgumentException("No puede repetir un ítem de checklist en la misma inspección.");
            InspectionChecklistItem item = checklistItems.findById(answerRequest.checklistItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ítem de checklist no encontrado"));
            if (!item.isActive()) throw new ConflictException("El ítem de checklist seleccionado está inactivo.");
            String observation = blankToNull(answerRequest.observation());
            String evidenceUrl = null;
            if (!answerRequest.compliant()) {
                if (observation == null) throw new IllegalArgumentException("Cada ítem no conforme debe incluir una observación.");
                MultipartFile evidence = photoAt(photos, answerRequest.evidencePhotoIndex());
                validatePhoto(evidence);
                evidenceUrl = storage.storeFile(evidence, "contracts/inspections");
            } else if (answerRequest.evidencePhotoIndex() != null) {
                MultipartFile evidence = photoAt(photos, answerRequest.evidencePhotoIndex());
                validatePhoto(evidence);
                evidenceUrl = storage.storeFile(evidence, "contracts/inspections");
            }
            inspection.addAnswer(new InspectionChecklistAnswer(inspection, item, answerRequest.compliant(), observation, evidenceUrl));
        }
        VehicleInspection saved = inspections.saveAndFlush(inspection);
        Integer mileageDifference = null; BigDecimal fuelDifference = null; BigDecimal extraMileageCharge = null;
        if (checkIn != null) {
            mileageDifference = request.mileage() - checkIn.getMileage();
            fuelDifference = checkIn.getFuelLevelPercent().subtract(request.fuelLevelPercent()).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            extraMileageCharge = calculateExtraMileageCharge(contract.getReservation().getMileagePlan(), mileageDifference);
            contract.getVehicle().setMileage(request.mileage());
        }
        return map(saved, mileageDifference, fuelDifference, extraMileageCharge);
    }

    private BigDecimal calculateExtraMileageCharge(MileagePlan plan, int mileageDifference) {
        if (plan == null || plan.getIncludedKm() == null || mileageDifference <= plan.getIncludedKm()) return BigDecimal.ZERO.setScale(2);
        return plan.getExtraKmRate().multiply(BigDecimal.valueOf(mileageDifference - plan.getIncludedKm())).setScale(2, RoundingMode.HALF_UP);
    }
    private MultipartFile photoAt(List<MultipartFile> photos, Integer index) {
        if (index == null || index < 0 || index >= photos.size()) throw new IllegalArgumentException("Cada ítem no conforme debe incluir una foto de evidencia válida.");
        return photos.get(index);
    }
    private void validatePhoto(MultipartFile file) {
        String contentType = file == null ? null : file.getContentType();
        if (contentType == null || !(contentType.equalsIgnoreCase("image/jpeg") || contentType.equalsIgnoreCase("image/jpg") || contentType.equalsIgnoreCase("image/png"))) throw new IllegalArgumentException("La evidencia debe ser una imagen JPG o PNG.");
        storage.validateFile(file);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private VehicleInspectionResponseDTO map(VehicleInspection inspection, Integer mileageDifference, BigDecimal fuelDifference, BigDecimal extraMileageCharge) {
        List<InspectionChecklistAnswerResponseDTO> answers = inspection.getAnswers().stream().map(answer -> new InspectionChecklistAnswerResponseDTO(answer.getChecklistItem().getId(), answer.getChecklistItem().getName(), answer.isCompliant(), answer.getObservation(), answer.getEvidencePhotoUrl())).toList();
        return new VehicleInspectionResponseDTO(inspection.getId(), inspection.getContract().getId(), inspection.getInspectionType(), inspection.getMileage(), inspection.getFuelLevelPercent(), inspection.getObservations(), inspection.getCreatedAt(), mileageDifference, fuelDifference, extraMileageCharge, answers);
    }
}
