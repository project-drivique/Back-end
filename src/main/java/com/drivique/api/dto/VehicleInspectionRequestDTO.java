package com.drivique.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record VehicleInspectionRequestDTO(
        @NotBlank @Pattern(regexp = "CHECK_IN|CHECK_OUT") String inspectionType,
        @NotNull @PositiveOrZero Integer mileage,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal fuelLevelPercent,
        @Size(max = 4000) String observations,
        @NotEmpty List<@Valid InspectionChecklistAnswerRequestDTO> answers
) {}
