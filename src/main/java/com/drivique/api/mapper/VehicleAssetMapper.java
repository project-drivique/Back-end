package com.drivique.api.mapper;

import com.drivique.api.dto.VehicleDocumentResponseDTO;
import com.drivique.api.dto.VehicleImageResponseDTO;
import com.drivique.api.model.VehicleDocument;
import com.drivique.api.model.VehicleImage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class VehicleAssetMapper {

    private VehicleAssetMapper() {}

    public static VehicleImageResponseDTO toDTO(VehicleImage img) {
        return new VehicleImageResponseDTO(
                img.getId(),
                img.getVehicle() != null ? img.getVehicle().getId() : null,
                img.getUrl(),
                img.isPrimary(),
                img.getSortOrder(),
                img.getCreatedAt()
        );
    }

    public static VehicleDocumentResponseDTO toDTO(VehicleDocument doc) {
        Long days = null;
        boolean expiringSoon = false;
        if (doc.getExpiresAt() != null) {
            days = ChronoUnit.DAYS.between(LocalDate.now(), doc.getExpiresAt());
            expiringSoon = days <= 30;
        }

        return new VehicleDocumentResponseDTO(
                doc.getId(),
                doc.getVehicle() != null ? doc.getVehicle().getId() : null,
                doc.getDocumentType(),
                doc.getDocumentNumber(),
                doc.getFileUrl(),
                doc.getIssuedAt(),
                doc.getExpiresAt(),
                doc.isActive(),
                expiringSoon,
                days,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
