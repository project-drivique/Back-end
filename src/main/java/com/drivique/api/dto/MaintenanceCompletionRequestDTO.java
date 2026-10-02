package com.drivique.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MaintenanceCompletionRequestDTO(
        @NotNull LocalDate completedDate,
        @NotNull @DecimalMin("0.00") BigDecimal cost
) {}
