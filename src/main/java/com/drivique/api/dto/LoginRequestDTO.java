package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "Credenciales para inicio de sesión")
public record LoginRequestDTO(
        @Schema(example = "admin@drivique.com")
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El formato de correo no es válido.")
        @Size(max = 254)
        String email,

        @Schema(example = "Secret123$Pass")
        @NotBlank(message = "La contraseña es obligatoria.")
        String password,

        @Schema(example = "Chrome on Windows 11")
        @Size(max = 255)
        String deviceInfo
) {}
