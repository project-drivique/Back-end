package com.drivique.api.service;

import com.drivique.api.dto.BranchStaffResponseDTO;
import com.drivique.api.exception.ConflictException;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Branch;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.BranchUserId;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchStaffService {
    private final BranchRepository branches;
    private final UserRepository users;
    private final BranchUserRepository assignments;

    public BranchStaffService(BranchRepository branches, UserRepository users, BranchUserRepository assignments) {
        this.branches = branches;
        this.users = users;
        this.assignments = assignments;
    }

    @Transactional
    public void assign(UUID branchId, UUID userId) {
        Branch branch = activeBranch(branchId);
        User user = users.findById(userId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!hasOperationalRole(user)) {
            throw new ConflictException("User does not have an operational role");
        }
        if (assignments.existsByUserIdAndBranchId(userId, branchId)) {
            throw new ConflictException("User already assigned to branch");
        }
        assignments.saveAndFlush(new BranchUser(user, branch));
    }

    @Transactional
    public void remove(UUID branchId, UUID userId) {
        activeBranch(branchId);
        if (!assignments.existsByUserIdAndBranchId(userId, branchId)) {
            throw new ResourceNotFoundException("Branch assignment not found");
        }
        assignments.deleteById(new BranchUserId(userId, branchId));
    }

    @Transactional(readOnly = true)
    public List<BranchStaffResponseDTO> list(UUID branchId) {
        activeBranch(branchId);
        return assignments.findByBranchIdOrderByAssignedAtAsc(branchId).stream().map(value -> {
            User user = value.getUser();
            return new BranchStaffResponseDTO(user.getId(), user.getFullName(), user.getEmail(),
                    user.getRoles().stream().map(Role::getCode).sorted().toList(), value.getAssignedAt());
        }).toList();
    }

    private Branch activeBranch(UUID id) {
        return branches.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Active branch not found"));
    }

    private boolean hasOperationalRole(User user) {
        return user.getRoles().stream().map(Role::getCode)
                .anyMatch(code -> code.equals("EMPLOYEE") || code.equals("BRANCH_ADMIN") || code.equals("SUPER_ADMIN"));
    }
}
