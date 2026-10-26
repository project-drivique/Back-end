package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyPasswordRequestDTO(@NotBlank @Size(max = 72) String password) {}
