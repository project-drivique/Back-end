package com.drivique.api.mapper;

import com.drivique.api.dto.VehicleCategoryResponseDTO;
import com.drivique.api.model.VehicleCategory;

public final class VehicleCategoryMapper {
    private VehicleCategoryMapper() {}

    public static VehicleCategoryResponseDTO toDTO(VehicleCategory category) {
        return new VehicleCategoryResponseDTO(
                category.getId(),
                category.getName(),
                category.getBaseDailyRate(),
                category.getSecurityDeposit(),
                category.isActive()
        );
    }
}
