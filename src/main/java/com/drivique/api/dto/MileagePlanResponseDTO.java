package com.drivique.api.dto;
import java.math.BigDecimal; import java.util.UUID;
public record MileagePlanResponseDTO(UUID id, String name, Integer includedKm, BigDecimal dailyRate, BigDecimal extraKmRate, boolean isActive) {}
