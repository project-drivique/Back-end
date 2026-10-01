package com.drivique.api.dto;
import java.util.UUID;
public record BrandResponseDTO(UUID id, String companyName, String logoUrl, String faviconUrl,
        String primaryColor, String secondaryColor, String accentColor, String defaultTheme) {}
