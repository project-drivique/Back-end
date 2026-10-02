package com.drivique.api.service;
import com.drivique.api.dto.*;
import com.drivique.api.exception.*;
import com.drivique.api.model.*;
import com.drivique.api.repository.*;
import java.util.*;
import org.springframework.cache.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service public class PricingCatalogService {
    private final AdditionalServiceRepository services; private final InsuranceCoverageRepository coverages;
    public PricingCatalogService(AdditionalServiceRepository services, InsuranceCoverageRepository coverages) { this.services = services; this.coverages = coverages; }
    @Transactional(readOnly = true) @Cacheable("additionalServices") public List<AdditionalServiceResponseDTO> services() { return services.findByActiveTrueOrderByNameAsc().stream().map(this::dto).toList(); }
    @Transactional(readOnly = true) @Cacheable("insuranceCoverages") public List<InsuranceCoverageResponseDTO> coverages() { return coverages.findByActiveTrueOrderByNameAsc().stream().map(this::dto).toList(); }
    @Transactional @CacheEvict(value = "additionalServices", allEntries = true) public AdditionalServiceResponseDTO create(AdditionalServiceRequestDTO input) { String name = input.name().strip(); if (services.existsByNameIgnoreCase(name)) throw new ConflictException("Additional service already exists"); return dto(services.saveAndFlush(new AdditionalService(name, input.dailyRate()))); }
    @Transactional @CacheEvict(value = "additionalServices", allEntries = true) public AdditionalServiceResponseDTO update(UUID id, AdditionalServiceRequestDTO input) { AdditionalService service = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Additional service not found")); String name = input.name().strip(); if (services.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Additional service already exists"); service.update(name, input.dailyRate()); return dto(services.saveAndFlush(service)); }
    @Transactional @CacheEvict(value = "insuranceCoverages", allEntries = true) public InsuranceCoverageResponseDTO create(InsuranceCoverageRequestDTO input) { String name = input.name().strip(); if (coverages.existsByNameIgnoreCase(name)) throw new ConflictException("Insurance coverage already exists"); return dto(coverages.saveAndFlush(new InsuranceCoverage(name, input.dailyRate(), input.description().strip()))); }
    @Transactional @CacheEvict(value = "insuranceCoverages", allEntries = true) public InsuranceCoverageResponseDTO update(UUID id, InsuranceCoverageRequestDTO input) { InsuranceCoverage coverage = coverages.findById(id).orElseThrow(() -> new ResourceNotFoundException("Insurance coverage not found")); String name = input.name().strip(); if (coverages.existsByNameIgnoreCaseAndIdNot(name, id)) throw new ConflictException("Insurance coverage already exists"); coverage.update(name, input.dailyRate(), input.description().strip()); return dto(coverages.saveAndFlush(coverage)); }
    private AdditionalServiceResponseDTO dto(AdditionalService value) { return new AdditionalServiceResponseDTO(value.getId(), value.getName(), value.getDailyRate(), value.isActive()); }
    private InsuranceCoverageResponseDTO dto(InsuranceCoverage value) { return new InsuranceCoverageResponseDTO(value.getId(), value.getName(), value.getDailyRate(), value.getDescription(), value.isActive()); }
}
