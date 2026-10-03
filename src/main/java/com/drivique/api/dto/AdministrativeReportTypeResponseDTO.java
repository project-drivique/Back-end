package com.drivique.api.dto;

import com.drivique.api.model.AdministrativeReportType;
import java.util.UUID;

public record AdministrativeReportTypeResponseDTO(
        UUID id,
        String code,
        String name,
        String description,
        boolean active
) {
    public static AdministrativeReportTypeResponseDTO fromEntity(AdministrativeReportType type) {
        return new AdministrativeReportTypeResponseDTO(
                type.getId(),
                type.getCode(),
                type.getName(),
                type.getDescription(),
                type.isActive()
        );
    }
}
