package com.drivique.api.controller;

import com.drivique.api.dto.BranchProfileDTO;
import com.drivique.api.dto.BranchProfileRequestDTO;
import com.drivique.api.service.BranchProfileService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/branch-profile")
@Tag(name = "Branch Profile", description = "Perfil de la sucursal del encargado")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'EMPLOYEE', 'SUPER_ADMIN')")
public class BranchProfileController {

    private final BranchProfileService service;

    public BranchProfileController(BranchProfileService service) {
        this.service = service;
    }

    @GetMapping
    public BranchProfileDTO get(@RequestParam(required = false) UUID branchId, Authentication authentication) {
        return service.get(authentication.getName(), branchId);
    }

    @PutMapping
    public BranchProfileDTO update(@RequestParam(required = false) UUID branchId,
                                   @Valid @RequestBody BranchProfileRequestDTO request, Authentication authentication) {
        return service.update(authentication.getName(), branchId, request);
    }
}
