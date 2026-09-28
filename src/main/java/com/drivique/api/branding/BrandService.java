package com.drivique.api.branding;

import com.drivique.api.common.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrandService {
    // Explicit allowlist: new database settings must never become public automatically.
    private static final List<String> PUBLIC_KEYS = List.of(
            "SESSION_IDLE_TIMEOUT_MINUTES", "PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES");
    private final BrandRepository brands;
    private final SecurityConfigurationRepository security;
    public BrandService(BrandRepository brands, SecurityConfigurationRepository security) {
        this.brands = brands;
        this.security = security;
    }
    @Transactional(readOnly = true)
    public BrandResponseDTO active() {
        return brands.findByActiveTrue().orElseThrow(() -> new ResourceNotFoundException("No active brand")).response();
    }
    @Transactional
    public BrandResponseDTO update(BrandRequestDTO dto) {
        if (dto.companyName().strip().isBlank()
                || dto.primaryColor().equalsIgnoreCase(dto.secondaryColor())
                || dto.primaryColor().equalsIgnoreCase(dto.accentColor())
                || dto.secondaryColor().equalsIgnoreCase(dto.accentColor()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        BrandConfiguration brand = brands.findActiveForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand"));
        brand.update(dto);
        return brands.saveAndFlush(brand).response();
    }
    @Transactional(readOnly = true)
    public List<SecurityConfigurationResponseDTO> publicSecurity() {
        return security.findByConfigKeyInOrderByConfigKeyAsc(PUBLIC_KEYS).stream()
                .map(SecurityConfiguration::response).toList();
    }
}
