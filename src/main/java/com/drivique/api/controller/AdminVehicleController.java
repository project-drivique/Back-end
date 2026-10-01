package com.drivique.api.controller;

import com.drivique.api.dto.*;
import com.drivique.api.service.AdminVehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/vehicles")
@Tag(name = "Fleet Admin", description = "Administrative management of fleet vehicles, plates, and VINs")
@PreAuthorize("hasAuthority('fleet:manage') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminVehicleController {

    private final AdminVehicleService service;

    public AdminVehicleController(AdminVehicleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar todos los vehículos registrados de forma paginada para administración")
    public PageResponseDTO<VehicleAdminResponseDTO> list(@PageableDefault(size = 20) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar el detalle administrativo de un vehículo por su ID")
    public VehicleAdminResponseDTO detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un nuevo vehículo en la flota")
    public VehicleAdminResponseDTO create(@Valid @RequestBody VehicleCreateRequestDTO input) {
        return service.create(input);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar kilometraje, tarifa y sede física de un vehículo")
    public VehicleAdminResponseDTO update(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleUpdateRequestDTO input
    ) {
        return service.update(id, input);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Actualizar el estado operativo de un vehículo (AVAILABLE, MAINTENANCE, OUT_OF_SERVICE)")
    public VehicleAdminResponseDTO updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody VehicleStatusUpdateRequestDTO input
    ) {
        return service.updateStatus(id, input);
    }

    @PatchMapping("/{id}/toggle-status")
    @Operation(summary = "Alternar estado activo/inactivo (borrado lógico) del vehículo")
    public VehicleAdminResponseDTO toggleStatus(@PathVariable UUID id) {
        return service.toggleStatus(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar lógicamente un vehículo de la flota")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
