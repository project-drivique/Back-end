package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record BrandRequestDTO(
        @NotBlank @Size(max = 100) String companyName,
        @Size(max = 700000, message = "El logo no puede superar 500 KB")
        @Pattern(regexp = "(?s)^(https?://\\S+|data:image/(png|jpeg|webp|svg\\+xml);base64,[A-Za-z0-9+/=]+)$",
                message = "El logo debe ser una URL o una imagen PNG, JPEG, WEBP o SVG") String logoUrl,
        @Size(max = 2048) @URL @Pattern(regexp = "https?://.+") String faviconUrl,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String primaryColor,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String secondaryColor,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String accentColor,
        @NotBlank @Pattern(regexp = "LIGHT|DARK|SYSTEM") String defaultTheme) {}
