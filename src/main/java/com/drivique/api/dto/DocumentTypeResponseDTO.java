package com.drivique.api.dto;

import com.drivique.api.model.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "DocumentTypeResponse", description = "Catálogo de tipo de documento KYC")
public record DocumentTypeResponseDTO(
        UUID id,
        String code,
        String name,
        String description,
        boolean requiresFrontAndBack,
        boolean mandatory
) {
    public static DocumentTypeResponseDTO fromEntity(DocumentType entity) {
        if (entity == null) return null;
        return new DocumentTypeResponseDTO(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isRequiresFrontAndBack(),
                entity.isMandatory()
        );
    }
}
