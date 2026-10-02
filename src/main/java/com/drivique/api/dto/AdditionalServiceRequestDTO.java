package com.drivique.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record AdditionalServiceRequestDTO(@NotBlank @Size(max = 120) String name, @NotNull @DecimalMin("0.00") BigDecimal dailyRate) {}
