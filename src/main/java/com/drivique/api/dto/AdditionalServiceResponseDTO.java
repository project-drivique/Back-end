package com.drivique.api.dto;
import java.math.BigDecimal; import java.util.UUID;
public record AdditionalServiceResponseDTO(UUID id, String name, BigDecimal dailyRate, boolean isActive) {}
