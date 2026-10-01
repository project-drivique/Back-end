package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record VehicleCategoryResponseDTO(
        UUID id,
        String name,
        BigDecimal baseDailyRate,
        BigDecimal securityDeposit,
        boolean isActive
) {}
