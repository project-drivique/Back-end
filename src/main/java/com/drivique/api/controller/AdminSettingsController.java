package com.drivique.api.controller;

import com.drivique.api.dto.SecuritySettingDTO;
import com.drivique.api.dto.SecuritySettingRequestDTO;
import com.drivique.api.service.SecuritySettingsAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin/settings")
@Tag(name = "Global settings", description = "Parámetros globales de seguridad editables por el administrador global")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSettingsController {

    private final SecuritySettingsAdminService service;

    public AdminSettingsController(SecuritySettingsAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Listar los parámetros globales editables")
    public List<SecuritySettingDTO> list() {
        return service.list();
    }

    @PutMapping("/{key}")
    @Operation(summary = "Cambiar un parámetro global", description = "Valida el rango, audita el cambio y guarda el valor anterior para poder revertirlo.")
    public SecuritySettingDTO update(@PathVariable String key, @Valid @RequestBody SecuritySettingRequestDTO body, Authentication authentication) {
        return service.update(key, body.value(), body.reason(), authentication.getName());
    }

    @PostMapping("/{key}/revert")
    @Operation(summary = "Revertir el último cambio de un parámetro")
    public SecuritySettingDTO revert(@PathVariable String key, Authentication authentication) {
        return service.revert(key, authentication.getName());
    }
}
