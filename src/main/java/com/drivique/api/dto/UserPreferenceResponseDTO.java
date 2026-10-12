package com.drivique.api.dto;

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
        boolean pushNotifications,
        boolean inAppNotifications,
        boolean marketingNotifications,
        Instant updatedAt
) {
    public UserPreferenceResponseDTO(
            UUID userId,
            UUID languageId,
            UUID currencyId,
            String themePreference,
            boolean emailNotifications,
            boolean smsNotifications,
            Instant updatedAt
    ) {
        this(userId, languageId, currencyId, themePreference, emailNotifications, smsNotifications, true, true, false, updatedAt);
    }
}
