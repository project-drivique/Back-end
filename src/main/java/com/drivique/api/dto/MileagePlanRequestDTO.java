package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record MileagePlanRequestDTO(@NotBlank @Size(max=120) String name, @PositiveOrZero Integer includedKm, @NotNull @DecimalMin("0.00") BigDecimal dailyRate, @NotNull @DecimalMin("0.00") BigDecimal extraKmRate) {}
