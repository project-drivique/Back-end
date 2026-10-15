package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RoleRequestDTO(
        @NotBlank(message = "El nombre del rol es obligatorio") @Size(max = 80) String name,
        @NotBlank(message = "La descripción es obligatoria") @Size(max = 255) String description,
        List<String> permissionCodes
) {}
