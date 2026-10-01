package com.drivique.api.dto;

import java.time.Instant;
import java.util.UUID;

public record VehicleImageResponseDTO(
        UUID id,
        UUID vehicleId,
        String url,
        boolean isPrimary,
        short sortOrder,
        Instant createdAt
) {}
