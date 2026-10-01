package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
public record LanguageRequestDTO(
        @NotBlank @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") @Schema(example = "es") String code,
        @NotBlank @Size(max = 50) @Schema(example = "Español") String name) {}
