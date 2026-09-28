package com.drivique.api.branding;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "brand_configurations", schema = "core")
public class BrandConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;
    @Column(name = "favicon_url", length = 2048)
    private String faviconUrl;
    @Column(name = "primary_color", nullable = false, length = 7)
    private String primaryColor;
    @Column(name = "secondary_color", nullable = false, length = 7)
    private String secondaryColor;
    @Column(name = "accent_color", nullable = false, length = 7)
    private String accentColor;
    @Column(name = "default_theme", nullable = false, length = 10)
    private String defaultTheme;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    protected BrandConfiguration() {}
    void update(BrandRequestDTO dto) {
        companyName = dto.companyName().strip();
        logoUrl = dto.logoUrl();
        faviconUrl = dto.faviconUrl();
        primaryColor = dto.primaryColor().toUpperCase(java.util.Locale.ROOT);
        secondaryColor = dto.secondaryColor().toUpperCase(java.util.Locale.ROOT);
        accentColor = dto.accentColor().toUpperCase(java.util.Locale.ROOT);
        defaultTheme = dto.defaultTheme();
    }
    BrandResponseDTO response() {
        return new BrandResponseDTO(id, companyName, logoUrl, faviconUrl,
                primaryColor, secondaryColor, accentColor, defaultTheme);
    }
}
