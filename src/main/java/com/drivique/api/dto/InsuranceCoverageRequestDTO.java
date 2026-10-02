package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record InsuranceCoverageRequestDTO(@NotBlank @Size(max = 120) String name, @NotNull @DecimalMin("0.00") BigDecimal dailyRate, @NotBlank @Size(max = 5000) String description) {}
