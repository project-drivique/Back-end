package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Detalle completo de una reserva de vehículo")
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
        List<DeliveryPointResponseDTO> deliveryPoints,
        Instant createdAt
) {
    public ReservationResponseDTO(
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
    ) {
        this(id, code, customerId, customerName, customerEmail, vehicleId, vehicleModel, vehiclePlate, status, statusName, pickupDate, returnDate, rentalDays, vehicleDailyRate, vehicleSubtotal, insuranceCoverageId, insuranceCoverageName, insuranceDailyRate, mileagePlanId, mileagePlanName, mileagePlanDailyRate, additionalServices, promotion, totalEstimated, cashPaymentBranchId, cashPaymentCode, cashPaymentExpiresAt, blocksAvailability, List.of(), createdAt);
    }
}
