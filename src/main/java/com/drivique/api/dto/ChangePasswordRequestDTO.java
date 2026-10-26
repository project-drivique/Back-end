package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequestDTO(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(max = 72) String newPassword
) {}
