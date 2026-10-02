package com.drivique.api.dto;

import java.util.UUID;

public record ContractClauseResponseDTO(
        UUID id,
        String version,
        short sortOrder,
        String title,
        String content,
        boolean isActive
) {
}
