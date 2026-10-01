package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MessageResponse", description = "Respuesta de mensaje informativo")
public record MessageResponseDTO(
        @Schema(example = "Operación realizada exitosamente.")
        String message
) {
    public static MessageResponseDTO of(String message) {
        return new MessageResponseDTO(message);
    }
}
