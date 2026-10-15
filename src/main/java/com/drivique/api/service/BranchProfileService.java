package com.drivique.api.service;

import com.drivique.api.config.Auditable;
import com.drivique.api.dto.BranchProfileDTO;
import com.drivique.api.dto.BranchProfileRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Branch;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchCategoryRepository;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.VehicleCategoryRepository;
import com.drivique.api.repository.VehicleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Ficha de la sucursal: el encargado solo ve y edita la suya; el administrador elige la sucursal. */
@Service
public class BranchProfileService {

    private final BranchRepository branches;
    private final BranchUserRepository assignments;
    private final BranchCategoryRepository branchCategories;
    private final VehicleCategoryRepository categories;
    private final VehicleRepository vehicles;
    private final StaffBranchScope scope;

    public BranchProfileService(BranchRepository branches, BranchUserRepository assignments,
                                BranchCategoryRepository branchCategories, VehicleCategoryRepository categories,
                                VehicleRepository vehicles, StaffBranchScope scope) {
        this.branches = branches;
        this.assignments = assignments;
        this.branchCategories = branchCategories;
        this.categories = categories;
        this.vehicles = vehicles;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public BranchProfileDTO get(String email, UUID requestedBranchId) {
        return toDto(resolve(email, requestedBranchId));
    }

    @Transactional
    @Auditable(domain = "LOCATION", entity = "Branch", action = "UPDATE", description = "Actualización del perfil de sucursal")
    public BranchProfileDTO update(String email, UUID requestedBranchId, BranchProfileRequestDTO request) {
        Branch branch = resolve(email, requestedBranchId);
        branch.updateProfile(request.address().strip(), request.phone().strip(),
                request.pickupInstructions() == null || request.pickupInstructions().isBlank() ? null : request.pickupInstructions().strip(),
                request.latitude(), request.longitude());
        return toDto(branches.saveAndFlush(branch));
    }

    private Branch resolve(String email, UUID requestedBranchId) {
        UUID branchId = scope.forStaff(email).orElse(requestedBranchId);
        if (branchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica la sucursal.");
        }
        return branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Sucursal no encontrada"));
    }

    private BranchProfileDTO toDto(Branch branch) {
        List<String> categoryNames = branchCategories.findByBranchId(branch.getId()).stream()
                .map(link -> categories.findById(link.getCategoryId()).map(c -> c.getName()).orElse(null))
                .filter(name -> name != null)
                .sorted()
                .toList();
        String manager = assignments.findByBranchIdOrderByAssignedAtAsc(branch.getId()).stream()
                .map(link -> link.getUser())
                .filter(user -> user.getRoles().stream().map(Role::getCode).anyMatch("BRANCH_ADMIN"::equals))
                .min(Comparator.comparing(User::getFirstName))
                .map(User::getFullName)
                .orElse(null);
        return new BranchProfileDTO(branch.getId(), branch.getName(), branch.getCity().getName(), branch.getAddress(),
                branch.getPhone(), branch.getPickupInstructions(), branch.getLatitude(), branch.getLongitude(),
                branch.getOpeningTime(), branch.getClosingTime(), branch.isActive(), branch.allowsCashPayment(), manager,
                vehicles.countByCurrentBranchId(branch.getId()), branch.getVehicleCapacity(), categoryNames);
    }
}
