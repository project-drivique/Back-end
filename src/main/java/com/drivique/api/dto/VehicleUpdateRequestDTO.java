package com.drivique.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record VehicleUpdateRequestDTO(
        @NotNull(message = "Mileage is required")
        @Min(value = 0, message = "Mileage cannot be negative")
        Integer mileage,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "0.00", message = "Daily rate cannot be negative")
        BigDecimal dailyRate,

        @NotNull(message = "Current branch ID is required")
        UUID currentBranchId,

        UUID statusId,

        @Size(max = 50, message = "Color cannot exceed 50 characters")
        String color,

        @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
        String mainImageUrl,

        Boolean isFeatured,

        @Min(value = 1, message = "Passenger capacity must be at least 1")
        Short passengerCapacity,

        @Min(value = 1, message = "Doors count must be at least 1")
        Short doorsCount,

        @Min(value = 0, message = "Trunk capacity must be non-negative")
        Integer trunkCapacityLiters
) {}
