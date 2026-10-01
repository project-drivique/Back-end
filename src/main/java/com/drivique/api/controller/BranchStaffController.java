package com.drivique.api.controller;

import com.drivique.api.dto.BranchStaffResponseDTO;
import com.drivique.api.service.BranchStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/branches/{branchId}/staff")
@Tag(name = "Branch staff", description = "Asignación de personal a sedes")
public class BranchStaffController {
    private final BranchStaffService service;

    public BranchStaffController(BranchStaffService service) {
        this.service = service;
    }

    @PostMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('branches:manage_staff') or hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Asignar empleado a sede")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    public void assign(@PathVariable UUID branchId, @PathVariable UUID userId) {
        service.assign(branchId, userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('branches:manage_staff') or hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Retirar empleado de sede")
    public void remove(@PathVariable UUID branchId, @PathVariable UUID userId) {
        service.remove(branchId, userId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('branches:manage_staff') or hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Listar personal asignado a sede")
    public List<BranchStaffResponseDTO> list(@PathVariable UUID branchId) {
        return service.list(branchId);
    }
}
