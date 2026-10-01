package com.drivique.api.dto;

import java.util.List;

public record GroupedFeaturesResponseDTO(
        String group,
        List<FeatureResponseDTO> features
) {}
