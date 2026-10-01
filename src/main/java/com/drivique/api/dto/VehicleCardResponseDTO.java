package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleCardResponseDTO(
        UUID id,
        String plate,
        String brandName,
        String model,
        UUID categoryId,
        String categoryName,
        String transmissionCode,
        String transmissionName,
        String fuelTypeCode,
        String fuelTypeName,
        UUID branchId,
        String branchName,
        String cityName,
        short year,
        String color,
        short passengerCapacity,
        Short doorsCount,
        Integer trunkCapacityLiters,
        int mileage,
        BigDecimal dailyRate,
        String mainImageUrl,
        boolean isFeatured,
        boolean isActive,
        boolean allowsReservation
) {}
