package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record GenerateContractRequestDTO(
        @NotNull(message = "El ID de la reserva es obligatorio para emitir el contrato")
        @Schema(description = "Identificador único UUID de la reserva confirmada", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID reservationId
) {
}
