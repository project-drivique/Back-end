package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Respuesta de autenticación con tokens y perfil")
public record AuthResponseDTO(
        @Schema(description = "Token JWT de acceso", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Token de refresco", example = "drivique_rf_9c4b2a8...")
        String refreshToken,

        @Schema(description = "Tipo de token", example = "Bearer")
        String tokenType,

        @Schema(description = "Tiempo de expiración del accessToken en segundos", example = "900")
        long expiresIn,

        UserProfileResponseDTO userProfile
) {
    public static AuthResponseDTO of(String accessToken, String refreshToken, long expiresIn, UserProfileResponseDTO userProfile) {
        return new AuthResponseDTO(accessToken, refreshToken, "Bearer", expiresIn, userProfile);
    }
}
