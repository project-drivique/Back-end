package com.drivique.api.catalog;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ExchangeRateResponseDTO(UUID id, String fromCurrency, String toCurrency, BigDecimal rate,
        String provider, Instant fetchedAt) {}
