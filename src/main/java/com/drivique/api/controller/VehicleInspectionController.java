package com.drivique.api.controller;

import com.drivique.api.dto.VehicleInspectionRequestDTO;
import com.drivique.api.dto.VehicleInspectionResponseDTO;
import com.drivique.api.service.VehicleInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/contracts/{contractId}/inspections")
@SecurityRequirement(name = "bearerAuth")
public class VehicleInspectionController {
    private final VehicleInspectionService service;
    public VehicleInspectionController(VehicleInspectionService service) { this.service = service; }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('fleet:manage') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Registrar inspección de entrega o devolución", description = "Registra CHECK_IN o CHECK_OUT con checklist y fotografías. Para ítems no conformes se exige observación y evidencia; CHECK_OUT calcula excesos de kilometraje.")
    public VehicleInspectionResponseDTO register(
            @PathVariable UUID contractId,
            @Valid @RequestPart("request") VehicleInspectionRequestDTO request,
            @RequestPart(value = "evidencePhotos", required = false) List<MultipartFile> evidencePhotos,
            Authentication authentication) {
        return service.register(contractId, request, evidencePhotos, authentication.getName());
    }
}
