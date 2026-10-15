package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PromotionResponseDTO(
        UUID id,
        String code,
        String name,
        String conditions,
        String offerType,
        String discountType,
        BigDecimal discountValue,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minimumAmount,
        int minimumRentalDays,
        Integer maxUsesLimit,
        int currentUsesCount,
        UUID vehicleCategoryId,
        String vehicleCategoryName,
        UUID vehicleId,
        String vehicleName,
        List<PromotionVehicleDTO> vehicles,
        String audience,
        boolean featured,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
