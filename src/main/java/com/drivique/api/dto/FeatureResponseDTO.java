package com.drivique.api.dto;

import java.util.UUID;

public record FeatureResponseDTO(
        UUID id,
        String name,
        String featureGroup,
        String icon,
        boolean isActive
) {}
