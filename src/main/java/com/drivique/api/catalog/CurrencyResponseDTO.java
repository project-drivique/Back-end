package com.drivique.api.catalog;
import java.util.UUID;
public record CurrencyResponseDTO(UUID id, String code, String name, String symbol, boolean isDefault, boolean isActive) {}
