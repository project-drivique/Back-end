package com.drivique.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

@Schema(name = "UpdateUserPreferenceRequest", description = "Solicitud de actualización de preferencias de usuario")
public record UpdateUserPreferenceRequestDTO(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID languageId,

        @Schema(example = "b2c3d4e5-f6a7-8901-bcde-f12345678901")
        UUID currencyId,

        @Schema(example = "DARK", allowableValues = {"LIGHT", "DARK", "SYSTEM"})
        @Pattern(regexp = "^(LIGHT|DARK|SYSTEM)$", message = "El tema debe ser LIGHT, DARK o SYSTEM.")
        String themePreference,

        @Schema(example = "true")
        Boolean emailNotifications,

        @Schema(example = "false")
        Boolean smsNotifications
) {}
