package com.drivique.api.model;


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
    public void update(String companyName, String logoUrl, String faviconUrl, String primaryColor,
            String secondaryColor, String accentColor, String defaultTheme) {
        this.companyName = companyName;
        this.logoUrl = logoUrl;
        this.faviconUrl = faviconUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
        this.accentColor = accentColor;
        this.defaultTheme = defaultTheme;
    }
    public UUID getId() { return id; }
    public String getCompanyName() { return companyName; }
    public String getLogoUrl() { return logoUrl; }
    public String getFaviconUrl() { return faviconUrl; }
    public String getPrimaryColor() { return primaryColor; }
    public String getSecondaryColor() { return secondaryColor; }
    public String getAccentColor() { return accentColor; }
    public String getDefaultTheme() { return defaultTheme; }
}
