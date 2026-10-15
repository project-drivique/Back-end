package com.drivique.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdminUserRequestDTO(
        @NotBlank(message = "El nombre es obligatorio") @Size(max = 200) String fullName,
        @NotBlank(message = "El correo es obligatorio") @Email(message = "El formato de correo no es válido") String email,
        @Size(max = 30) String phone,
        @Size(max = 50) String documentNumber,
        @NotBlank(message = "El rol es obligatorio")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,39}$", message = "Rol inválido") String roleCode,
        UUID branchId
) {}
