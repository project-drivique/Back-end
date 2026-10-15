package com.drivique.api.controller;

import com.drivique.api.service.BranchCategoryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/branch-categories")
public class BranchCategoryController {

    private final BranchCategoryService service;

    public BranchCategoryController(BranchCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public Map<UUID, List<UUID>> all() {
        return service.all();
    }

    @PutMapping("/{branchId}/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    public void enable(@PathVariable UUID branchId, @PathVariable UUID categoryId) {
        service.enable(branchId, categoryId);
    }

    @DeleteMapping("/{branchId}/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'SUPER_ADMIN')")
    public void disable(@PathVariable UUID branchId, @PathVariable UUID categoryId) {
        service.disable(branchId, categoryId);
    }
}
