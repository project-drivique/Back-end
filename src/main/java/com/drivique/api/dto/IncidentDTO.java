package com.drivique.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IncidentDTO(
        UUID id,
        String code,
        String category,
        String status,
        String priority,
        String subject,
        String description,
        UUID vehicleId,
        String vehiclePlate,
        String vehicleModel,
        UUID reservationId,
        String reservationCode,
        UUID branchId,
        String branchName,
        UUID reporterId,
        String reporterName,
        String reporterEmail,
        UUID assignedToId,
        String assignedToName,
        Instant slaDueAt,
        boolean slaBreached,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        List<String> allowedTransitions,
        List<Attachment> attachments,
        List<TimelineEntry> timeline) {

    public record Attachment(UUID id, String originalName, String contentType, long sizeBytes, Instant createdAt) {
    }

    /** type: MESSAGE, STATUS o ASSIGNMENT. */
    public record TimelineEntry(
            String type,
            UUID authorId,
            String authorName,
            String authorRole,
            String message,
            String fromStatus,
            String toStatus,
            List<Attachment> attachments,
            Instant createdAt) {
    }
}
