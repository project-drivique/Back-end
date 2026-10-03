package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendNotificationRequestDTO(
        @NotNull(message = "El ID del usuario es obligatorio")
        UUID userId,

        @NotBlank(message = "El canal de notificación es obligatorio")
        @Pattern(regexp = "EMAIL|SMS|PUSH|IN_APP", message = "El canal debe ser EMAIL, SMS, PUSH o IN_APP")
        String channel,

        @NotBlank(message = "El tipo de notificación es obligatorio")
        @Pattern(regexp = "GENERAL|SECURITY|RESERVATION|PROMOTION", message = "El tipo debe ser GENERAL, SECURITY, RESERVATION o PROMOTION")
        String type,

        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 200, message = "El asunto no puede exceder 200 caracteres")
        String subject,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 5000, message = "El mensaje no puede exceder 5000 caracteres")
        String message,

        UUID referenceId
) {
}
