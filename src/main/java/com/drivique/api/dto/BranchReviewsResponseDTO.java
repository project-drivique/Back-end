package com.drivique.api.dto;

import java.util.List;
import java.util.UUID;

public record BranchReviewsResponseDTO(
        UUID branchId,
        double averageRating,
        int totalReviews,
        List<BranchReviewResponseDTO> reviews
) {}
