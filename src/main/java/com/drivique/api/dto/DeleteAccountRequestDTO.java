package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequestDTO(@NotBlank String password) {}
