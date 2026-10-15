package com.drivique.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Reporte calculado en el servidor: columnas, página de filas, indicadores globales y filtros aplicados. */
public record ReportDataDTO(
        String type,
        String title,
        List<Column> columns,
        List<Map<String, Object>> rows,
        List<Metric> summary,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<String, Object> filters,
        Instant generatedAt) {

    /** kind: text, number, money, percent o date. */
    public record Column(String key, String label, String kind) {}

    public record Metric(String key, String label, Object value, String kind) {}
}
