package com.drivique.api.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "ForgotPasswordRequest", description = "Solicitud de recuperación de contraseña")
public record ForgotPasswordRequestDTO(
        @Schema(example = "user@drivique.com")
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El formato de correo no es válido.")
        String email
) {}
