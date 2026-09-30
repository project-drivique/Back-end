package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(name = "AssignRolesRequest", description = "Solicitud de asignación de roles a un usuario")
public record AssignRolesRequestDTO(
        @Schema(example = "[\"CUSTOMER\", \"EMPLOYEE\"]")
        @NotEmpty(message = "Debe proporcionar al menos un rol a asignar.")
        List<String> roleCodes
) {}
