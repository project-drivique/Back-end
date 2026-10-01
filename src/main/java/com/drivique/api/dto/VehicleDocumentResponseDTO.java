package com.drivique.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleDocumentResponseDTO(
        UUID id,
        UUID vehicleId,
        String documentType,
        String documentNumber,
        String fileUrl,
        LocalDate issuedAt,
        LocalDate expiresAt,
        boolean isActive,
        boolean isExpiringSoon,
        Long daysUntilExpiration,
        Instant createdAt,
        Instant updatedAt
) {}
