package com.drivique.api.dto;
import java.util.UUID;
public record CurrencyResponseDTO(UUID id, String code, String name, String symbol, boolean isDefault, boolean isActive) {}
