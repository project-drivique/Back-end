package com.drivique.api.controller;

import com.drivique.api.dto.VehicleCategoryRequestDTO;
import com.drivique.api.dto.VehicleCategoryResponseDTO;
import com.drivique.api.service.VehicleCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/vehicle-categories")
@Tag(name = "Fleet Categories", description = "Vehicle categories and reference rates management")
public class VehicleCategoryController {

    private final VehicleCategoryService service;

    public VehicleCategoryController(VehicleCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Consultar categorías de vehículos activas")
    public List<VehicleCategoryResponseDTO> active() {
        return service.active();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar detalle de una categoría de vehículo")
    public VehicleCategoryResponseDTO detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear nueva categoría de vehículo")
    public VehicleCategoryResponseDTO create(@Valid @RequestBody VehicleCategoryRequestDTO input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar categoría de vehículo")
    public VehicleCategoryResponseDTO update(@PathVariable UUID id, @Valid @RequestBody VehicleCategoryRequestDTO input) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Inactivar o activar lógicamente una categoría de vehículo")
    public VehicleCategoryResponseDTO toggleStatus(@PathVariable UUID id) {
        return service.toggleStatus(id);
    }
}
