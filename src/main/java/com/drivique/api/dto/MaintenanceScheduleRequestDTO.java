package com.drivique.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceScheduleRequestDTO(
        @NotNull UUID vehicleId,
        @NotNull UUID maintenanceTypeId,
        @NotNull LocalDate scheduledDate,
        @NotNull @DecimalMin("0.00") BigDecimal cost,
        @Size(max = 5000) String description
) {}
