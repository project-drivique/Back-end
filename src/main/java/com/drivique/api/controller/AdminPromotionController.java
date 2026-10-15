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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin/promotions")
@Tag(name = "Admin Promotions", description = "Gestión de promociones y cupones")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'BRANCH_ADMIN')")
public class AdminPromotionController {

    private final PromotionAdminService service;

    public AdminPromotionController(PromotionAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<PromotionResponseDTO> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponseDTO create(@Valid @RequestBody PromotionRequestDTO request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PromotionResponseDTO update(@PathVariable UUID id, @Valid @RequestBody PromotionRequestDTO request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public PromotionResponseDTO toggleActive(@PathVariable UUID id) {
        return service.toggleActive(id);
    }

    @PatchMapping("/{id}/featured")
    public PromotionResponseDTO toggleFeatured(@PathVariable UUID id) {
        return service.toggleFeatured(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
