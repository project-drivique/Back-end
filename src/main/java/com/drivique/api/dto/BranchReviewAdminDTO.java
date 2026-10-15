package com.drivique.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Reseña de sucursal tal como la ve el encargado o el administrador. */
public record BranchReviewAdminDTO(
        UUID id,
        UUID branchId,
        String branchName,
        String customerName,
        String reservationCode,
        String vehicleName,
        short rating,
        String comment,
        String managerReply,
        Instant repliedAt,
        Instant createdAt
) {}
