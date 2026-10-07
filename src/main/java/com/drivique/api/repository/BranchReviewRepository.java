package com.drivique.api.repository;

import com.drivique.api.model.BranchReview;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchReviewRepository extends JpaRepository<BranchReview, UUID> {
    boolean existsByReservationIdAndBranchId(UUID reservationId, UUID branchId);

    List<BranchReview> findByBranchIdOrderByCreatedAtDesc(UUID branchId);

    @Query("SELECT AVG(r.rating) FROM BranchReview r WHERE r.branch.id = :branchId")
    Double averageByBranchId(@Param("branchId") UUID branchId);
}
