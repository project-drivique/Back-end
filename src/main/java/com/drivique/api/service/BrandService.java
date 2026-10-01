package com.drivique.api.service;

import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.dto.BrandRequestDTO;
import com.drivique.api.dto.BrandResponseDTO;
import com.drivique.api.model.BrandConfiguration;
import com.drivique.api.mapper.BrandMapper;
import com.drivique.api.repository.BrandRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrandService {
    private final BrandRepository brands;
    public BrandService(BrandRepository brands) { this.brands = brands; }
    @Transactional(readOnly = true)
    public BrandResponseDTO active() {
        return BrandMapper.response(brands.findByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No active brand")));
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
        BrandMapper.update(brand, dto);
        return BrandMapper.response(brands.saveAndFlush(brand));
    }
}
