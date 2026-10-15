package com.drivique.api.dto;

import com.drivique.api.model.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponseDTO(
        UUID id,
        String domainName,
        String entityName,
        UUID entityId,
        String operation,
        String result,
        UUID actorUserId,
        String actorEmail,
        String actorFullName,
        String actorRole,
        UUID branchId,
        String branchName,
        String ipAddress,
        String description,
        String oldData,
        String newData,
        Instant createdAt
) {
    /** Forma anterior (sin rol del actor). */
    public AuditLogResponseDTO(UUID id, String domainName, String entityName, UUID entityId, String operation, String result,
                               UUID actorUserId, String actorEmail, String actorFullName, UUID branchId, String branchName,
                               String ipAddress, String description, String oldData, String newData, Instant createdAt) {
        this(id, domainName, entityName, entityId, operation, result, actorUserId, actorEmail, actorFullName, null, branchId,
                branchName, ipAddress, description, oldData, newData, createdAt);
    }

    public static AuditLogResponseDTO fromEntity(AuditLog log) {
        return new AuditLogResponseDTO(
                log.getId(),
                log.getDomainName(),
                log.getEntityName(),
                log.getEntityId(),
                log.getOperation(),
                log.getResult(),
                log.getActor() != null ? log.getActor().getId() : null,
                log.getActor() != null ? log.getActor().getEmail() : null,
                log.getActor() != null ? log.getActor().getFullName() : null,
                log.getActor() == null ? null : log.getActor().getRoles().stream().map(com.drivique.api.model.Role::getCode).min(String::compareTo).orElse(null),
                log.getBranch() != null ? log.getBranch().getId() : null,
                log.getBranch() != null ? log.getBranch().getName() : null,
                log.getIpAddress(),
                log.getDescription(),
                log.getOldData(),
                log.getNewData(),
                log.getCreatedAt()
        );
    }
}
