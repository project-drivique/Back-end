package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "RoleResponse", description = "Detalle del rol con permisos asociados")
public record RoleResponseDTO(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        List<PermissionResponseDTO> permissions
) {}
