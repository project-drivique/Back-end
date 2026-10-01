package com.drivique.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VehicleImageRequestDTO(
        @NotBlank(message = "Image URL is required")
        @Size(max = 1000, message = "Image URL cannot exceed 1000 characters")
        String url,

        Boolean isPrimary,

        @NotNull(message = "Sort order is required")
        @Min(value = 1, message = "Sort order must be at least 1")
        Short sortOrder
) {}
