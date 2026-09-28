package com.drivique.api.branding;
import java.util.UUID;
public record BrandResponseDTO(UUID id, String companyName, String logoUrl, String faviconUrl,
        String primaryColor, String secondaryColor, String accentColor, String defaultTheme) {}
