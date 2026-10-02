package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateExtensionRequestDTO(
        @NotNull(message = "La nueva fecha de devolución solicitada es obligatoria")
        @Schema(description = "Nueva fecha y hora solicitada de devolución", example = "2026-10-15T18:00:00Z")
        Instant requestedReturnDate
) {
}
