package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record RecordAuditLogRequestDTO(
        String domainName,
        @NotBlank(message = "El nombre de la entidad es obligatorio")
        String entityName,
        UUID entityId,
        @NotBlank(message = "La operación es obligatoria")
        String operation,
        String result,
        UUID actorUserId,
        UUID branchId,
        String ipAddress,
        String description,
        String oldData,
        String newData
) {}
