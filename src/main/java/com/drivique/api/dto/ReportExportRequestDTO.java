package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;

/** Pide al servidor calcular un reporte y entregarlo como archivo. */
public record ReportExportRequestDTO(
        @NotBlank(message = "El tipo de reporte es obligatorio") String type,
        @NotBlank(message = "El formato es obligatorio")
        @Pattern(regexp = "PDF|EXCEL|CSV", message = "El formato debe ser PDF, EXCEL o CSV") String format,
        LocalDate from,
        LocalDate to,
        UUID branchId,
        String status,
        String category,
        String priority,
        String search) {}
