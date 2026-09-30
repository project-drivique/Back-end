package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Solicitud de rotación de tokens")
public record RefreshTokenRequestDTO(
        @Schema(description = "Refresh token previamente emitido", example = "drivique_rf_abc123...")
        @NotBlank(message = "El refresh token es obligatorio.")
        String refreshToken
) {}
