package com.drivique.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Usuario tal como lo ve el administrador (rol, sucursal asignada y reservas activas). */
public record AdminUserDTO(
        UUID id,
        String fullName,
        String email,
        String phone,
        String documentNumber,
        String roleCode,
        String roleName,
        String accountStatus,
        boolean active,
        UUID branchId,
        String branchName,
        long activeReservations,
        Instant createdAt
) {}
