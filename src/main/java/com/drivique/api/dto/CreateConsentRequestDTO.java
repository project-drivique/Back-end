package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateConsentRequestDTO(
        @NotBlank @Size(max = 60) @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String consentType,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String documentVersion
) {}
