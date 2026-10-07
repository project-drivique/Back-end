package com.drivique.api.repository;

import com.drivique.api.model.BranchUser;
import com.drivique.api.model.BranchUserId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchUserRepository extends JpaRepository<BranchUser, BranchUserId> {
    boolean existsByUserIdAndBranchId(UUID userId, UUID branchId);
    List<BranchUser> findByBranchIdOrderByAssignedAtAsc(UUID branchId);
    Optional<BranchUser> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM BranchUser b WHERE b.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
