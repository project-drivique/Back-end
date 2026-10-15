package com.drivique.api.repository;

import com.drivique.api.model.BranchCategory;
import com.drivique.api.model.BranchCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BranchCategoryRepository extends JpaRepository<BranchCategory, BranchCategoryId> {
    boolean existsByBranchIdAndCategoryId(UUID branchId, UUID categoryId);

    java.util.List<BranchCategory> findByBranchId(UUID branchId);
}
