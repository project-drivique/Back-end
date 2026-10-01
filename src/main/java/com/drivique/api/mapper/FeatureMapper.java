package com.drivique.api.mapper;

import com.drivique.api.dto.FeatureResponseDTO;
import com.drivique.api.model.Feature;

public final class FeatureMapper {

    private FeatureMapper() {}

    public static FeatureResponseDTO toDTO(Feature feature) {
        return new FeatureResponseDTO(
                feature.getId(),
                feature.getName(),
                feature.getFeatureGroup(),
                feature.getIcon(),
                feature.isActive()
        );
    }
}
