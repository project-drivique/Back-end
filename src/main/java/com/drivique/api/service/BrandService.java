package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.BrandRequestDTO;
import com.drivique.api.dto.BrandResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.BrandMapper;
import com.drivique.api.model.BrandConfiguration;
import com.drivique.api.repository.BrandRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrandService {
    private final BrandRepository brands;
    private final ObjectMapper mapper;

    @Value("${drivique.brand.default-name:Drivique}")
    private String defaultName = "Drivique";
    @Value("${drivique.brand.default-primary:#2563EB}")
    private String defaultPrimary = "#2563EB";
    @Value("${drivique.brand.default-secondary:#1E3A8A}")
    private String defaultSecondary = "#1E3A8A";
    @Value("${drivique.brand.default-accent:#60A5FA}")
    private String defaultAccent = "#60A5FA";

    public BrandService(BrandRepository brands, @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper mapper) {
        this.brands = brands;
        this.mapper = mapper != null ? mapper : new ObjectMapper();
    }

    @Transactional(readOnly = true)
    public BrandResponseDTO active() {
        return BrandMapper.response(brands.findByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand")));
    }

    @Transactional
    @Auditable(domain = "SYSTEM", entity = "BrandConfiguration", action = "UPDATE", description = "Actualización de la marca", includeArgs = false, includeResult = false)
    public BrandResponseDTO update(BrandRequestDTO dto) {
        if (dto.companyName().strip().isBlank()
                || dto.primaryColor().equalsIgnoreCase(dto.secondaryColor())
                || dto.primaryColor().equalsIgnoreCase(dto.accentColor())
                || dto.secondaryColor().equalsIgnoreCase(dto.accentColor()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        BrandConfiguration brand = brands.findActiveForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand"));
        brand.setPreviousSnapshot(snapshot(brand));
        BrandMapper.update(brand, dto);
        return BrandMapper.response(brands.saveAndFlush(brand));
    }

    /** Vuelve a la marca que había antes del último cambio; el cambio actual queda como "anterior". */
    @Transactional
    @Auditable(domain = "SYSTEM", entity = "BrandConfiguration", action = "UPDATE", description = "Restauración de la marca anterior", includeArgs = false, includeResult = false)
    public BrandResponseDTO restorePrevious() {
        BrandConfiguration brand = brands.findActiveForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand"));
        if (brand.getPreviousSnapshot() == null) {
            throw new ConflictException("No hay una marca anterior para restaurar");
        }
        Map<String, Object> previous = read(brand.getPreviousSnapshot());
        String current = snapshot(brand);
        brand.update(text(previous, "companyName"), text(previous, "logoUrl"), text(previous, "faviconUrl"),
                text(previous, "primaryColor"), text(previous, "secondaryColor"), text(previous, "accentColor"),
                text(previous, "defaultTheme"));
        brand.setPreviousSnapshot(current);
        return BrandMapper.response(brands.saveAndFlush(brand));
    }

    /** Restablece los valores originales de Drivique (sin logo). */
    @Transactional
    @Auditable(domain = "SYSTEM", entity = "BrandConfiguration", action = "UPDATE", description = "Restauración de la marca original", includeArgs = false, includeResult = false)
    public BrandResponseDTO restoreDefault() {
        BrandConfiguration brand = brands.findActiveForUpdate()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand"));
        brand.setPreviousSnapshot(snapshot(brand));
        brand.update(defaultName, null, brand.getFaviconUrl(), defaultPrimary.toUpperCase(), defaultSecondary.toUpperCase(),
                defaultAccent.toUpperCase(), brand.getDefaultTheme());
        return BrandMapper.response(brands.saveAndFlush(brand));
    }

    private String snapshot(BrandConfiguration brand) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("companyName", brand.getCompanyName());
        values.put("logoUrl", brand.getLogoUrl());
        values.put("faviconUrl", brand.getFaviconUrl());
        values.put("primaryColor", brand.getPrimaryColor());
        values.put("secondaryColor", brand.getSecondaryColor());
        values.put("accentColor", brand.getAccentColor());
        values.put("defaultTheme", brand.getDefaultTheme());
        try {
            return mapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la marca anterior", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La marca anterior guardada no es válida", e);
        }
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : value.toString();
    }
}
