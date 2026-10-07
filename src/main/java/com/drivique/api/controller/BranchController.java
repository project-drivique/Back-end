package com.drivique.api.controller;

import com.drivique.api.dto.*;
import com.drivique.api.service.BranchService;
import com.drivique.api.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/branches")
public class BranchController {
    private final BranchService service;
    private final ReviewService reviewService;

    public BranchController(BranchService service, ReviewService reviewService) {
        this.service = service;
        this.reviewService = reviewService;
    }

    @GetMapping
    @Operation(summary="Consultar sedes activas")
    public List<BranchResponseDTO> active() { return service.active(); }

    @GetMapping("/{id}")
    public BranchResponseDTO detail(@PathVariable UUID id) { return service.detail(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    public BranchResponseDTO create(@Valid @RequestBody BranchRequestDTO input) { return service.create(input); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    public BranchResponseDTO update(@PathVariable UUID id, @Valid @RequestBody BranchRequestDTO input) { return service.update(id, input); }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    public BranchResponseDTO toggle(@PathVariable UUID id) {
        return service.toggle(id);
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Consultar reseñas de sede")
    public BranchReviewsResponseDTO reviews(@PathVariable UUID id) {
        return reviewService.branchReviews(id);
    }
}
