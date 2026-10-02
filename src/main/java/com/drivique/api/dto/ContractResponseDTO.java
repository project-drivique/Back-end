package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ContractResponseDTO(
        UUID id,
        String contractNumber,
        UUID reservationId,
        String reservationCode,
        UUID customerId,
        String customerFullName,
        String customerEmail,
        UUID vehicleId,
        String vehicleModel,
        String vehiclePlate,
        String statusCode,
        String statusName,
        UUID pickupBranchId,
        String pickupBranchName,
        UUID returnBranchId,
        String returnBranchName,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        BigDecimal baseAmount,
        BigDecimal securityDeposit,
        String signatureUrl,
        String pdfUrl,
        Instant signedAt,
        List<ContractClauseResponseDTO> clauses,
        Instant createdAt,
        Instant updatedAt
) {
}
