package com.drivique.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record VehicleCreateRequestDTO(
        @NotBlank(message = "Plate is required")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "Plate must only contain uppercase letters, numbers, and hyphens")
        @Size(min = 3, max = 10, message = "Plate must be between 3 and 10 characters")
        String plate,

        @NotBlank(message = "VIN is required")
        @Size(min = 17, max = 17, message = "VIN must be exactly 17 characters")
        @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$", message = "VIN format is invalid")
        String vin,

        @NotNull(message = "Brand ID is required")
        UUID brandId,

        @NotNull(message = "Category ID is required")
        UUID categoryId,

        @NotNull(message = "Transmission type ID is required")
        UUID transmissionTypeId,

        @NotNull(message = "Fuel type ID is required")
        UUID fuelTypeId,

        UUID statusId,

        @NotNull(message = "Current branch ID is required")
        UUID currentBranchId,

        @NotBlank(message = "Model is required")
        @Size(max = 100, message = "Model name cannot exceed 100 characters")
        String model,

        @NotNull(message = "Year is required")
        @Min(value = 1900, message = "Year must be at least 1900")
        @Max(value = 2100, message = "Year cannot exceed 2100")
        Short year,

        @Size(max = 50, message = "Color cannot exceed 50 characters")
        String color,

        @NotNull(message = "Passenger capacity is required")
        @Min(value = 1, message = "Passenger capacity must be at least 1")
        Short passengerCapacity,

        @Min(value = 1, message = "Doors count must be at least 1")
        Short doorsCount,

        @Min(value = 0, message = "Trunk capacity must be non-negative")
        Integer trunkCapacityLiters,

        @NotNull(message = "Mileage is required")
        @Min(value = 0, message = "Mileage cannot be negative")
        Integer mileage,

        @NotNull(message = "Daily rate is required")
        @DecimalMin(value = "0.00", message = "Daily rate cannot be negative")
        BigDecimal dailyRate,

        @Size(max = 2048, message = "Image URL cannot exceed 2048 characters")
        String mainImageUrl,

        Boolean isFeatured
) {}
