package com.drivique.api.controller;

import com.drivique.api.dto.AssignVehicleFeaturesRequestDTO;
import com.drivique.api.dto.FeatureResponseDTO;
import com.drivique.api.dto.GroupedFeaturesResponseDTO;
import com.drivique.api.service.FeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/features")
@Tag(name = "Fleet Features", description = "Vehicle equipment and features catalog")
public class FeatureController {

    private final FeatureService featureService;

    public FeatureController(FeatureService featureService) {
        this.featureService = featureService;
    }

    @GetMapping
    @Operation(summary = "Listar catálogo de equipamiento y características agrupadas por categoría")
    public List<GroupedFeaturesResponseDTO> listGrouped() {
        return featureService.listGrouped();
    }

    @GetMapping("/all")
    @Operation(summary = "Listar catálogo completo de equipamiento activo de forma plana")
    public List<FeatureResponseDTO> listAll() {
        return featureService.listAll();
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Listar equipamiento asociado a un vehículo específico")
    public List<FeatureResponseDTO> listByVehicle(@PathVariable UUID vehicleId) {
        return featureService.listByVehicle(vehicleId);
    }
}
