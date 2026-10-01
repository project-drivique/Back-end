package com.drivique.api.controller;

import com.drivique.api.dto.SecurityConfigurationResponseDTO;
import com.drivique.api.service.SecurityConfigurationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@Tag(name = "Public settings", description = "Parámetros no sensibles para clientes")
public class SecurityConfigurationController {
    private final SecurityConfigurationService service;
    public SecurityConfigurationController(SecurityConfigurationService service) { this.service = service; }
    @GetMapping("/security-configurations")
    @Operation(summary = "Consultar parámetros públicos para clientes", description = "Solo SESSION_IDLE_TIMEOUT_MINUTES y PASSWORD_RESET_TOKEN_EXPIRATION_MINUTES. No publica claves desconocidas, controles de bloqueo ni MFA. Los valores son cadenas; no incluye descripciones internas.")
    @ApiResponse(responseCode = "200", description = "Lista pública ordenada por configKey; vacía si faltan las claves")
    public List<SecurityConfigurationResponseDTO> security() { return service.publicSettings(); }
}
