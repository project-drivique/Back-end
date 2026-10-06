package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Información de cuenta social vinculada")
public record SocialAccountResponseDTO(
        @Schema(description = "ID del registro de vinculación")
        UUID id,

        @Schema(description = "Proveedor OAuth", example = "GOOGLE")
        String provider,

        @Schema(description = "Correo reportado por el proveedor", example = "usuario@gmail.com")
        String email,

        @Schema(description = "Fecha de vinculación")
        Instant linkedAt
) {}
