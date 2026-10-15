package com.drivique.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record BranchProfileRequestDTO(
        @NotBlank(message = "La dirección es obligatoria") @Size(max = 255) String address,
        @NotBlank(message = "El teléfono es obligatorio") @Size(max = 30) String phone,
        @Size(max = 500) String pickupInstructions,
        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90") @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90") BigDecimal latitude,
        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180") @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180") BigDecimal longitude
) {}
