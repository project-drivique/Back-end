package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "UserRolesResponse", description = "Respuesta con los roles asignados al usuario")
public record UserRolesResponseDTO(
        UUID userId,
        String email,
        List<String> roles
) {}
