package com.drivique.api.dto;

import java.util.Map;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Reporte ya generado en pantalla (tipo, filtros y datos) que se guarda en el historial del servidor. */
public record ReportHistoryRequestDTO(
        @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{2,49}$", message = "Tipo de reporte inválido") String typeCode,
        @NotBlank @Size(max = 100) String typeName,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Pattern(regexp = "PDF|EXCEL|WORD|CSV", message = "Formato inválido") String format,
        @Min(0) int totalRecords,
        Map<String, Object> filters,
        @NotNull Object snapshot
) {}
