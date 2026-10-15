package com.drivique.api.service;

import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.BranchCategory;
import com.drivique.api.model.BranchCategoryId;
import com.drivique.api.repository.BranchCategoryRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.VehicleCategoryRepository;
import com.drivique.api.repository.VehicleRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Categorías de vehículo que ofrece cada sucursal. */
@Service
public class BranchCategoryService {

    private final BranchCategoryRepository branchCategories;
    private final BranchRepository branches;
    private final VehicleCategoryRepository categories;
    private final VehicleRepository vehicles;
    private final StaffBranchScope scope;

    public BranchCategoryService(BranchCategoryRepository branchCategories, BranchRepository branches,
            VehicleCategoryRepository categories, VehicleRepository vehicles, StaffBranchScope scope) {
        this.branchCategories = branchCategories;
        this.branches = branches;
        this.categories = categories;
        this.vehicles = vehicles;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> all() {
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        for (BranchCategory item : branchCategories.findAll()) {
            result.computeIfAbsent(item.getBranchId(), k -> new ArrayList<>()).add(item.getCategoryId());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID branchId, UUID categoryId) {
        return branchCategories.existsByBranchIdAndCategoryId(branchId, categoryId);
    }

    @Transactional
    public void enable(UUID branchId, UUID categoryId) {
        assertAllowed(branchId);
        if (!branches.existsById(branchId)) throw new ResourceNotFoundException("Sucursal no encontrada");
        if (categories.findById(categoryId).filter(c -> c.isActive()).isEmpty()) {
            throw new ResourceNotFoundException("Categoría no encontrada o inactiva");
        }
        if (!branchCategories.existsByBranchIdAndCategoryId(branchId, categoryId)) {
            branchCategories.save(new BranchCategory(branchId, categoryId));
        }
    }

    @Transactional
    public void disable(UUID branchId, UUID categoryId) {
        assertAllowed(branchId);
        if (vehicles.existsByCurrentBranchIdAndCategoryIdAndActiveTrue(branchId, categoryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No puedes deshabilitar la categoría porque la sucursal tiene vehículos de esa categoría.");
        }
        branchCategories.deleteById(new BranchCategoryId(branchId, categoryId));
    }

    private void assertAllowed(UUID branchId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Optional<UUID> allowed = scope.forAuthentication(authentication);
        if (allowed.isPresent() && !allowed.get().equals(branchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes gestionar las categorías de tu sucursal.");
        }
    }
}
