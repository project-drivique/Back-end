package com.drivique.api.controller;

import com.drivique.api.dto.*;
import com.drivique.api.service.MaintenanceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('fleet:manage') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class MaintenanceController {
    private final MaintenanceService service;
    public MaintenanceController(MaintenanceService service) { this.service = service; }

    @PostMapping("/maintenances") @ResponseStatus(HttpStatus.CREATED)
    public VehicleMaintenanceResponseDTO schedule(@Valid @RequestBody MaintenanceScheduleRequestDTO input) { return service.schedule(input); }
    @PatchMapping("/maintenances/{id}/complete")
    public VehicleMaintenanceResponseDTO complete(@PathVariable UUID id, @Valid @RequestBody MaintenanceCompletionRequestDTO input) { return service.complete(id, input); }
    @GetMapping("/vehicles/{id}/maintenances")
    public List<VehicleMaintenanceResponseDTO> history(@PathVariable UUID id) { return service.history(id); }
}
