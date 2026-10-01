package com.drivique.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record VehicleCategoryRequestDTO(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @NotNull(message = "baseDailyRate is required")
        @DecimalMin(value = "0.00", message = "baseDailyRate must be greater than or equal to 0")
        BigDecimal baseDailyRate,

        @NotNull(message = "securityDeposit is required")
        @DecimalMin(value = "0.00", message = "securityDeposit must be greater than or equal to 0")
        BigDecimal securityDeposit
) {}
