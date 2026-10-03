package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record GenerateReportRequestDTO(
        @NotBlank(message = "El tipo de reporte es obligatorio")
        @Pattern(regexp = "FLEET_OCCUPANCY|REVENUE_SUMMARY|AUDIT_TRAIL|MAINTENANCE", message = "Tipo de reporte inválido")
        String reportType,

        @NotBlank(message = "El formato de exportación es obligatorio")
        @Pattern(regexp = "PDF|EXCEL|CSV|WORD", message = "Formato debe ser PDF, EXCEL, CSV o WORD")
        String format,

        LocalDate startDate,
        LocalDate endDate
) {}
