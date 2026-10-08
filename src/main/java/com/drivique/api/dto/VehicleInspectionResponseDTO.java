package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VehicleInspectionResponseDTO(UUID id, UUID contractId, String inspectionType, int mileage, BigDecimal fuelLevelPercent, String observations, Instant createdAt, Integer mileageDifference, BigDecimal fuelLevelDifference, BigDecimal mileageCharge, BigDecimal fuelCharge, BigDecimal damageCharge, BigDecimal totalCharge, List<InspectionChecklistAnswerResponseDTO> answers) {
    public BigDecimal extraMileageCharge(){return mileageCharge;}
}
