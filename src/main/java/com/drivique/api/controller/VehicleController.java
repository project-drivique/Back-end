package com.drivique.api.controller;

import com.drivique.api.dto.PageResponseDTO;
import com.drivique.api.dto.VehicleCardResponseDTO;
import com.drivique.api.service.VehicleSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/vehicles")
@Tag(name = "Fleet Search", description = "Public fleet search and catalog endpoints")
public class VehicleController {

    private final VehicleSearchService searchService;
    private final com.drivique.api.service.FeatureService featureService;

    public VehicleController(VehicleSearchService searchService, com.drivique.api.service.FeatureService featureService) {
        this.searchService = searchService;
        this.featureService = featureService;
    }

    @GetMapping("/search")
    @Operation(summary = "Búsqueda y filtrado dinámico de vehículos con paginación")
    public PageResponseDTO<VehicleCardResponseDTO> search(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID transmissionId,
            @RequestParam(required = false) UUID fuelId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "dailyRate,asc") String sort
    ) {
        Sort sortObj = Sort.by(Sort.Direction.ASC, "dailyRate");
        if (sort != null && sort.contains(",")) {
            String[] parts = sort.split(",");
            String property = parts[0].trim();
            Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            sortObj = Sort.by(direction, property);
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), sortObj);
        return searchService.search(branchId, categoryId, transmissionId, fuelId, minPrice, maxPrice, featured, pageable);
    }

    @GetMapping("/featured")
    @Operation(summary = "Listado de vehículos destacados para la portada")
    public List<VehicleCardResponseDTO> featured() {
        return searchService.featured();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar ficha técnica pública de un vehículo")
    public VehicleCardResponseDTO detail(@PathVariable UUID id) {
        return searchService.detail(id);
    }

    @GetMapping("/{id}/features")
    @Operation(summary = "Consultar características y equipamiento de un vehículo")
    public List<com.drivique.api.dto.FeatureResponseDTO> features(@PathVariable UUID id) {
        return featureService.listByVehicle(id);
    }
}
