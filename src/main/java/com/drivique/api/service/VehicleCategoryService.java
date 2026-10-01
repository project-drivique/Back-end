package com.drivique.api.service;

import com.drivique.api.dto.VehicleCategoryRequestDTO;
import com.drivique.api.dto.VehicleCategoryResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleCategoryMapper;
import com.drivique.api.model.VehicleCategory;
import com.drivique.api.repository.VehicleCategoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class VehicleCategoryService {

    private final VehicleCategoryRepository categories;

    public VehicleCategoryService(VehicleCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    @Cacheable("vehicleCategories")
    public List<VehicleCategoryResponseDTO> active() {
        return categories.findByActiveTrueOrderByNameAsc().stream()
                .map(VehicleCategoryMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleCategoryResponseDTO detail(UUID id) {
        return categories.findById(id)
                .map(VehicleCategoryMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found"));
    }

    @Transactional
    @CacheEvict(value = "vehicleCategories", allEntries = true)
    public VehicleCategoryResponseDTO create(VehicleCategoryRequestDTO input) {
        String name = input.name().strip();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Vehicle category already exists");
        }
        try {
            VehicleCategory category = new VehicleCategory(name, input.baseDailyRate(), input.securityDeposit());
            return VehicleCategoryMapper.toDTO(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Vehicle category conflicts with database constraints");
        }
    }

    @Transactional
    @CacheEvict(value = "vehicleCategories", allEntries = true)
    public VehicleCategoryResponseDTO update(UUID id, VehicleCategoryRequestDTO input) {
        VehicleCategory category = categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found"));
        String name = input.name().strip();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("Vehicle category already exists");
        }
        category.update(name, input.baseDailyRate(), input.securityDeposit());
        try {
            return VehicleCategoryMapper.toDTO(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Vehicle category conflicts with database constraints");
        }
    }

    @Transactional
    @CacheEvict(value = "vehicleCategories", allEntries = true)
    public VehicleCategoryResponseDTO toggleStatus(UUID id) {
        VehicleCategory category = categories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle category not found"));
        category.toggleStatus();
        return VehicleCategoryMapper.toDTO(categories.saveAndFlush(category));
    }
}
