package com.drivique.api.service;

import com.drivique.api.dto.PageResponseDTO;
import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.mapper.VehicleMapper;
import com.drivique.api.model.Vehicle;
import com.drivique.api.repository.VehicleRepository;
import com.drivique.api.specification.VehicleSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class VehicleSearchService {

    private final VehicleRepository vehicleRepository;

    public VehicleSearchService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<VehicleCardResponseDTO> search(
            UUID branchId,
            UUID categoryId,
            UUID transmissionId,
            UUID fuelId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean featured,
            Pageable pageable
    ) {
        Specification<Vehicle> spec = VehicleSpecification.withDynamicFilters(
                branchId, categoryId, transmissionId, fuelId, minPrice, maxPrice, featured
        );

        Page<Vehicle> page = vehicleRepository.findAll(spec, pageable);
        return PageResponseDTO.of(page.map(VehicleMapper::toCardDTO));
    }

    @Transactional(readOnly = true)
    public List<VehicleCardResponseDTO> featured() {
        return vehicleRepository.findFeaturedVehicles().stream()
                .map(VehicleMapper::toCardDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehicleCardResponseDTO detail(UUID id) {
        return vehicleRepository.findByIdAndActiveTrue(id)
                .map(VehicleMapper::toCardDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found"));
    }
}
