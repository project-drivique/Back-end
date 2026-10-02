package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponseDTO(
        UUID id,
        String code,
        UUID customerId,
        String customerName,
        String customerEmail,
        UUID vehicleId,
        String vehicleModel,
        String vehiclePlate,
        String status,
        String statusName,
        Instant pickupDate,
        Instant returnDate,
        int rentalDays,
        BigDecimal vehicleDailyRate,
        BigDecimal vehicleSubtotal,
        UUID insuranceCoverageId,
        String insuranceCoverageName,
        BigDecimal insuranceDailyRate,
        UUID mileagePlanId,
        String mileagePlanName,
        BigDecimal mileagePlanDailyRate,
        List<ReservationAdditionalServiceItemDTO> additionalServices,
        ReservationPromotionItemDTO promotion,
        BigDecimal totalEstimated,
        UUID cashPaymentBranchId,
        String cashPaymentCode,
        Instant cashPaymentExpiresAt,
        boolean blocksAvailability,
        Instant createdAt
) {}
