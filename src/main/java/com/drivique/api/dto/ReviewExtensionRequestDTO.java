package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReviewExtensionRequestDTO(
        @NotBlank(message = "El estado del dictamen es obligatorio (APPROVED o REJECTED)")
        @Pattern(regexp = "APPROVED|REJECTED", message = "El estado debe ser APPROVED o REJECTED")
        @Schema(description = "Dictamen de aprobación o rechazo", example = "APPROVED")
        String status,

        @Schema(description = "Comentarios o motivo del dictamen", example = "Extensión aprobada con disponibilidad de vehículo")
        String comments
) {
}
