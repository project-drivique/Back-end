package com.drivique.api.branding;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@Tag(name = "Brand and public settings", description = "Identidad visual y parámetros públicos")
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

    @GetMapping("/security-configurations")
    @Operation(summary = "Consultar parámetros públicos para clientes", description = "Solo SESSION_IDLE_TIMEOUT_MINUTES y PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES. No publica claves desconocidas, controles de bloqueo ni MFA. Los valores son cadenas; no incluye descripciones internas.")
    @ApiResponse(responseCode = "200", description = "Lista pública ordenada por configKey; vacía si faltan las claves")
    public List<SecurityConfigurationResponseDTO> security() { return service.publicSecurity(); }
}
