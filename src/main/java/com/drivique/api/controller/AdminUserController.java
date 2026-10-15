package com.drivique.api.controller;

import com.drivique.api.dto.AdminUserDTO;
import com.drivique.api.dto.AdminUserRequestDTO;
import com.drivique.api.service.AdminUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/users")
@Tag(name = "Admin Users", description = "Administración de cuentas y roles")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserDTO> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDTO create(@Valid @RequestBody AdminUserRequestDTO request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserDTO update(@PathVariable UUID id, @Valid @RequestBody AdminUserRequestDTO request, Authentication authentication) {
        return service.update(id, request, authentication.getName());
    }

    @PatchMapping("/{id}/status")
    public AdminUserDTO toggleStatus(@PathVariable UUID id, Authentication authentication) {
        return service.toggleStatus(id, authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(id, authentication.getName());
    }
}
