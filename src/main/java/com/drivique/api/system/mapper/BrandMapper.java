package com.drivique.api.system.mapper;

import com.drivique.api.system.dto.BrandRequestDTO;
import com.drivique.api.system.dto.BrandResponseDTO;
import com.drivique.api.system.entity.BrandConfiguration;
import java.util.Locale;

public final class BrandMapper {
    private BrandMapper() {}
    public static void update(BrandConfiguration brand, BrandRequestDTO dto) {
        brand.update(dto.companyName().strip(), dto.logoUrl(), dto.faviconUrl(),
                dto.primaryColor().toUpperCase(Locale.ROOT), dto.secondaryColor().toUpperCase(Locale.ROOT),
                dto.accentColor().toUpperCase(Locale.ROOT), dto.defaultTheme());
    }
    public static BrandResponseDTO response(BrandConfiguration brand) {
        return new BrandResponseDTO(brand.getId(), brand.getCompanyName(), brand.getLogoUrl(),
                brand.getFaviconUrl(), brand.getPrimaryColor(), brand.getSecondaryColor(),
                brand.getAccentColor(), brand.getDefaultTheme());
    }
}
