package com.drivique.api.repository;

import com.drivique.api.model.BranchUser;
import com.drivique.api.model.BranchUserId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchUserRepository extends JpaRepository<BranchUser, BranchUserId> {
    boolean existsByUserIdAndBranchId(UUID userId, UUID branchId);
    List<BranchUser> findByBranchIdOrderByAssignedAtAsc(UUID branchId);
    Optional<BranchUser> findByUserId(UUID userId);
}
