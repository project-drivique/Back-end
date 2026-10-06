package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Solicitud para vincular una cuenta social a un usuario autenticado")
public record SocialLinkRequestDTO(
        @NotBlank(message = "El proveedor es obligatorio.")
        @Pattern(regexp = "^(?i)(GOOGLE|FACEBOOK)$", message = "Proveedor no soportado. Valores permitidos: GOOGLE, FACEBOOK")
        @Schema(description = "Proveedor OAuth/OIDC", example = "GOOGLE")
        String provider,

        @Schema(description = "ID Token emitido por el proveedor", example = "eyJhbGciOiJSUzI1NiIs...")
        String idToken,

        @Schema(description = "Access Token emitido por el proveedor", example = "EAA...")
        String accessToken,

        @Schema(description = "Authorization code obtenido tras consentimiento", example = "4/0AWgavdf...")
        String authCode,

        @Schema(description = "Code verifier para PKCE", example = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
        String codeVerifier,

        @Schema(description = "URI de redirección registrada", example = "https://drivique.com/auth/callback")
        String redirectUri,

        @Schema(description = "Nonce criptográfico anti-replay", example = "n-0S6_WzA2Mj")
        String nonce
) {
    public String normalizedProvider() {
        return provider != null ? provider.trim().toUpperCase() : "";
    }
}
