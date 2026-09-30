package com.drivique.api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserPreferenceResponse", description = "Preferencias de configuración e idioma del usuario")
public record UserPreferenceResponseDTO(
        UUID userId,
        UUID languageId,
        UUID currencyId,
        String themePreference,
        boolean emailNotifications,
        boolean smsNotifications,
        Instant updatedAt
) {}
