package com.drivique.api.controller;

import com.drivique.api.dto.BrandRequestDTO;
import com.drivique.api.dto.BrandResponseDTO;
import com.drivique.api.service.BrandService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@Tag(name = "Brand configuration", description = "Identidad visual y tema")
public class BrandController {
    private final BrandService service;
    public BrandController(BrandService service) { this.service = service; }
    @GetMapping("/brand-configurations/active")
    @Operation(summary = "Consultar la marca activa", description = "Público. Colores, URLs y tema; 404 si no hay marca activa.")
    @ApiResponse(responseCode = "200", description = "Configuración activa")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public BrandResponseDTO active() { return service.active(); }

    @PutMapping("/brand-configurations")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar la marca activa (SUPER_ADMIN)", description = "Reemplaza los campos editables. Colores HEX distintos; tema LIGHT, DARK o SYSTEM. URLs HTTP/HTTPS opcionales (null). No crea ni activa marcas.")
    @ApiResponse(responseCode = "200", description = "Marca actualizada")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    public BrandResponseDTO update(@Valid @RequestBody BrandRequestDTO dto) { return service.update(dto); }

    @PostMapping("/brand-configurations/restore-previous")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restaurar la marca anterior (SUPER_ADMIN)")
    public BrandResponseDTO restorePrevious() { return service.restorePrevious(); }

    @PostMapping("/brand-configurations/restore-default")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Restablecer la marca original (SUPER_ADMIN)")
    public BrandResponseDTO restoreDefault() { return service.restoreDefault(); }

}
