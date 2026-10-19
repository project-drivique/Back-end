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
        UUID nationalityId,

        @Schema(example = "Calle 10 # 20-30, Medellín")
        @Size(max = 255, message = "La dirección no puede exceder 255 caracteres.")
        String address,

        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
        UUID documentTypeId,

        @Schema(example = "CC")
        String documentTypeCode,

        @Schema(example = "1020304050")
        @Size(max = 50, message = "El número de documento no puede exceder 50 caracteres.")
        String documentNumber
) {
    public UpdateUserProfileRequestDTO(String firstName, String lastName, String phone, LocalDate birthDate, UUID nationalityId) {
        this(firstName, lastName, phone, birthDate, nationalityId, null, null, null, null);
    }

    public UpdateUserProfileRequestDTO(String firstName, String lastName, String phone, LocalDate birthDate, UUID nationalityId, String address) {
        this(firstName, lastName, phone, birthDate, nationalityId, address, null, null, null);
    }
}
