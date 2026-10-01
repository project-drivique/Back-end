package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "ResetPasswordRequest", description = "Solicitud de restablecimiento de contraseña mediante OTP")
public record ResetPasswordRequestDTO(
        @Schema(example = "user@drivique.com")
        @NotBlank(message = "El correo electrónico es obligatorio.")
        @Email(message = "El formato de correo no es válido.")
        String email,

        @Schema(example = "123456", description = "Código numérico de 6 dígitos")
        @NotBlank(message = "El código de verificación es obligatorio.")
        @Pattern(regexp = "^[0-9]{6}$", message = "El código debe tener exactamente 6 dígitos numéricos.")
        String code,

        @Schema(example = "NewSecret123$Pass", description = "Nueva contraseña que cumpla con la política activa")
        @NotBlank(message = "La nueva contraseña es obligatoria.")
        String newPassword
) {}
