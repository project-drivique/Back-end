package com.drivique.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PriceQuoteRequestDTO(@NotNull UUID vehicleId, @NotNull LocalDate pickupDate,
        @NotNull LocalDate returnDate, @NotNull UUID insuranceId, @NotNull UUID mileagePlanId,
        List<UUID> additionalServiceIds, String couponCode) {}
