package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleAdminResponseDTO(
        UUID id,
        String plate,
        String vin,
        UUID brandId,
        String brandName,
        UUID categoryId,
        String categoryName,
        UUID transmissionTypeId,
        String transmissionTypeCode,
        String transmissionTypeName,
        UUID fuelTypeId,
        String fuelTypeCode,
        String fuelTypeName,
        UUID statusId,
        String statusCode,
        String statusName,
        boolean allowsReservation,
        UUID currentBranchId,
        String currentBranchName,
        String currentCityName,
        String model,
        short year,
        String color,
        short passengerCapacity,
        Short doorsCount,
        Integer trunkCapacityLiters,
        int mileage,
        BigDecimal dailyRate,
        String mainImageUrl,
        boolean isFeatured,
        boolean isActive
) {}
