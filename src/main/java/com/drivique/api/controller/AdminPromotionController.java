package com.drivique.api.controller;

import com.drivique.api.dto.PromotionRequestDTO;
import com.drivique.api.dto.PromotionResponseDTO;
import com.drivique.api.service.PromotionAdminService;
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
@RequestMapping("/v1/admin/promotions")
@Tag(name = "Admin Promotions", description = "Gestión de promociones y cupones")
@SecurityRequirement(name = "bearerAuth")
public class AdminPromotionController {

    private final PromotionAdminService service;

    public AdminPromotionController(PromotionAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN')")
    public List<PromotionResponseDTO> list(Authentication authentication) {
        return service.listForUser(authentication != null ? authentication.getName() : null);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public PromotionResponseDTO create(@Valid @RequestBody PromotionRequestDTO request, Authentication authentication) {
        return service.create(request, authentication != null ? authentication.getName() : null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public PromotionResponseDTO update(@PathVariable UUID id, @Valid @RequestBody PromotionRequestDTO request, Authentication authentication) {
        return service.update(id, request, authentication != null ? authentication.getName() : null);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public PromotionResponseDTO toggleActive(@PathVariable UUID id, Authentication authentication) {
        return service.toggleActive(id, authentication != null ? authentication.getName() : null);
    }

    @PatchMapping("/{id}/featured")
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public PromotionResponseDTO toggleFeatured(@PathVariable UUID id, Authentication authentication) {
        return service.toggleFeatured(id, authentication != null ? authentication.getName() : null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BRANCH_ADMIN')")
    public void delete(@PathVariable UUID id, Authentication authentication) {
        service.delete(id, authentication != null ? authentication.getName() : null);
    }
}
