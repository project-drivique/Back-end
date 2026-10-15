package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SecuritySettingRequestDTO(
        @NotBlank(message = "El valor es obligatorio") @Size(max = 20) String value,
        @Size(max = 300) String reason) {}
