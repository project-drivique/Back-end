package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "PermissionResponse", description = "Detalle del permiso del sistema")
public record PermissionResponseDTO(
        UUID id,
        String code,
        String name,
        String description
) {}
