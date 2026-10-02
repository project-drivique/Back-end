package com.drivique.api.dto;
import java.math.BigDecimal; import java.util.UUID;
public record InsuranceCoverageResponseDTO(UUID id, String name, BigDecimal dailyRate, String description, boolean isActive) {}
