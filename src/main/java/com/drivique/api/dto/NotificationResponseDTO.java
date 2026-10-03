package com.drivique.api.dto;

import com.drivique.api.model.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponseDTO(
        UUID id,
        UUID userId,
        String channel,
        String type,
        String subject,
        String message,
        UUID referenceId,
        boolean isRead,
        Instant sentAt,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponseDTO fromEntity(Notification notification) {
        return new NotificationResponseDTO(
                notification.getId(),
                notification.getUser() != null ? notification.getUser().getId() : null,
                notification.getChannel(),
                notification.getType(),
                notification.getSubject(),
                notification.getMessage(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
