package com.drivique.api.system.service;

import com.drivique.api.common.exception.ResourceNotFoundException;
import com.drivique.api.system.dto.BrandRequestDTO;
import com.drivique.api.system.dto.BrandResponseDTO;
import com.drivique.api.system.entity.BrandConfiguration;
import com.drivique.api.system.mapper.BrandMapper;
import com.drivique.api.system.repository.BrandRepository;
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
