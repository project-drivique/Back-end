package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "UpdateUserProfileRequest", description = "Datos modificables del perfil del usuario (email y número de documento bloqueados)")
public record UpdateUserProfileRequestDTO(
        @Schema(example = "Carlos")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres.")
        String firstName,

        @Schema(example = "Gomez")
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 100, message = "El apellido no puede exceder 100 caracteres.")
        String lastName,

        @Schema(example = "+573001234567")
        @Size(max = 30, message = "El teléfono no puede exceder 30 caracteres.")
        String phone,

        @Schema(example = "1990-05-15")
        LocalDate birthDate,

        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID nationalityId
) {}
