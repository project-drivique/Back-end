package com.drivique.api.notifications;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.drivique.api.model.BrandConfiguration;
import com.drivique.api.repository.BrandRepository;
import com.drivique.api.service.BrandEmailTheme;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrandEmailThemeTests {

    @Test
    void replacesOriginalBluesWithActiveBrandColors() {
        BrandRepository repo = mock(BrandRepository.class);
        BrandConfiguration brand = mock(BrandConfiguration.class);
        when(brand.getPrimaryColor()).thenReturn("#E11D48");
        when(brand.getSecondaryColor()).thenReturn("#881337");
        when(brand.getAccentColor()).thenReturn("#FDA4AF");
        when(brand.getCompanyName()).thenReturn("Mi Marca");
        when(repo.findByActiveTrue()).thenReturn(Optional.of(brand));

        String html = new BrandEmailTheme(repo).apply(
                "<div style=\"background: linear-gradient(135deg, #1e3a8a 0%, #2563EB 100%)\"><h1>DRIVIQUE</h1></div>"
                        .replace("<h1>DRIVIQUE</h1>", "<h1 style=\"x\">DRIVIQUE</h1>"));

        assertTrue(html.contains("#881337"));
        assertTrue(html.contains("#E11D48"));
        assertFalse(html.toLowerCase().contains("#2563eb"));
        assertTrue(html.contains(">MI MARCA</h1>"));
    }
}
