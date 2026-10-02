package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RentalExtensionResponseDTO(
        UUID id,
        UUID reservationId,
        String reservationCode,
        Instant currentReturnDate,
        Instant requestedReturnDate,
        int additionalDays,
        BigDecimal vehicleDailyRate,
        BigDecimal insuranceDailyRate,
        BigDecimal mileageDailyRate,
        BigDecimal additionalAmount,
        String status,
        UUID reviewedByUserId,
        String reviewedByUserName,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
