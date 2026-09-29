package com.drivique.api.system.controller;

import com.drivique.api.system.dto.CurrencyResponseDTO;
import com.drivique.api.system.dto.ExchangeRateResponseDTO;
import com.drivique.api.system.dto.LanguageRequestDTO;
import com.drivique.api.system.dto.LanguageResponseDTO;
import com.drivique.api.system.service.CatalogService;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/v1")
@Tag(name = "System catalogs", description = "Idiomas, monedas y tasas de cambio")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service) { this.service = service; }

    @GetMapping("/languages")
    @Operation(summary = "Consultar idiomas activos")
    @ApiResponse(responseCode = "200", description = "Idiomas ordenados por código")
    public List<LanguageResponseDTO> languages() { return service.languages(); }

    @GetMapping("/currencies")
    @Operation(summary = "Consultar monedas activas")
    @ApiResponse(responseCode = "200", description = "Monedas ordenadas por código")
    public List<CurrencyResponseDTO> currencies() { return service.currencies(); }

    @GetMapping("/exchange-rates/latest")
    @Operation(summary = "Consultar la última tasa almacenada por cada par de monedas activas")
    @ApiResponse(responseCode = "200", description = "No inventa tasas ni garantiza cotizaciones en tiempo real")
    public List<ExchangeRateResponseDTO> latest() { return service.latest(); }

    @PostMapping("/languages")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear idioma activo (SUPER_ADMIN)")
    @ApiResponse(responseCode = "201", description = "Idioma creado; no modifica el idioma predeterminado")
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    @ResponseStatus(HttpStatus.CREATED)
    public LanguageResponseDTO create(@Valid @RequestBody LanguageRequestDTO input) { return service.create(input); }

    @PatchMapping("/languages/{id}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Alternar estado activo de un idioma (SUPER_ADMIN)")
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    public LanguageResponseDTO toggle(@PathVariable UUID id) { return service.toggle(id); }

    @PostMapping("/exchange-rates/sync")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Sincronizar tasas desde el proveedor aprobado (SUPER_ADMIN)",
            description = "Consulta Frankfurter v2 y guarda las tasas de las monedas activas. Devuelve 503 si falla el proveedor.")
    @ApiResponse(responseCode = "200", description = "Tasas guardadas")
    @ApiResponse(responseCode = "503", description = "Proveedor no configurado, inválido o no disponible")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403")
    public List<ExchangeRateResponseDTO> sync() { return service.sync(); }
}
