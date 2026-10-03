package com.drivique.api.dto;import jakarta.validation.constraints.NotNull;import java.util.UUID;public record BranchReviewRequestDTO(@NotNull UUID branchId,@NotNull ReviewRequestDTO review){}
