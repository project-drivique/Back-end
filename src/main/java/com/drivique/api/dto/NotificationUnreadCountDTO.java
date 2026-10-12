package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NotificationUnreadCountResponse", description = "Contador de notificaciones no leídas")
public record NotificationUnreadCountDTO(
        @Schema(description = "Cantidad de notificaciones no leídas", example = "3")
        long unreadCount
) {}
