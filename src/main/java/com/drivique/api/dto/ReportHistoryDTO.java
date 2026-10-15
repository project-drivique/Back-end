package com.drivique.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Entrada del historial de reportes; el snapshot solo se incluye al consultar un reporte puntual. */
public record ReportHistoryDTO(
        UUID id,
        String code,
        String title,
        String typeCode,
        String typeName,
        String format,
        int totalRecords,
        Object filters,
        Object snapshot,
        Instant generatedAt,
        String generatedByName,
        String generatedByEmail,
        String generatedByRole
) {}
