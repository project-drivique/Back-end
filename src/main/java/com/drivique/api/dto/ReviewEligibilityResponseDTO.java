package com.drivique.api.dto;

public record ReviewEligibilityResponseDTO(
        boolean canReviewVehicle,
        boolean canReviewBranch,
        VehicleReviewResponseDTO vehicleReview,
        java.util.UUID branchId,
        String branchName,
        BranchReviewResponseDTO branchReview
) {}
