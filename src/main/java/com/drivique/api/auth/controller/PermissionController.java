package com.drivique.api.auth.controller;

import com.drivique.api.auth.dto.PermissionResponseDTO;
import com.drivique.api.auth.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/permissions")
@Tag(name = "Roles & Permissions", description = "Matriz RBAC: Gestión de roles, permisos y asignaciones")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final RbacService rbacService;

    public PermissionController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAuthority('roles:read')")
    @Operation(summary = "Listar permisos del sistema", description = "Retorna todos los permisos disponibles en la matriz RBAC. Requiere autoridad roles:read o SUPER_ADMIN.")
    @ApiResponse(responseCode = "200", description = "Lista de permisos obtenida exitosamente")
    @ApiResponse(responseCode = "401", description = "No autenticado")
    @ApiResponse(responseCode = "403", description = "No autorizado")
    public List<PermissionResponseDTO> getPermissions() {
        return rbacService.getAllPermissions();
    }
}
