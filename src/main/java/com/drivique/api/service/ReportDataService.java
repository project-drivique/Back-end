package com.drivique.api.service;

import com.drivique.api.dto.ReportDataDTO;
import com.drivique.api.dto.ReportDataDTO.Column;
import com.drivique.api.dto.ReportDataDTO.Metric;
import com.drivique.api.service.ReportQueryCatalog.MetricDef;
import com.drivique.api.service.ReportQueryCatalog.Spec;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reportes administrativos calculados en la base de datos: filtros, paginación e indicadores salen del
 * servidor. Un encargado solo ve su sucursal; el administrador global ve todas.
 */
@Service
public class ReportDataService {

    public static final List<String> TYPES =
            List.of("RESERVATIONS", "REVENUE_SUMMARY", "FLEET_OCCUPANCY", "VEHICLES", "BRANCHES", "INCIDENTS",
                    "CONTRACTS", "USERS", "PROMOTIONS", "EXECUTIVE_SUMMARY", "INCIDENTS_MAINTENANCE");

    /** Filtros comunes; cada reporte usa los que le aplican. */
    public record ReportFilter(LocalDate from, LocalDate to, UUID branchId, String status, String category, String priority,
                          String search) {
        ReportFilter withBranch(UUID branch) {
            return new ReportFilter(from, to, branch, status, category, priority, search);
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final StaffBranchScope scope;

    @Value("${app.reports.max-page-size:100}")
    private int maxPageSize;

    @Value("${app.reports.max-export-rows:5000}")
    private int maxExportRows;

    @Value("${app.reports.max-range-days:366}")
    private int maxRangeDays;

    public ReportDataService(NamedParameterJdbcTemplate jdbc, StaffBranchScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    public int maxExportRows() {
        return maxExportRows;
    }

    /** Nombre de la sucursal para mostrar los filtros aplicados en los archivos exportados. */
    @Transactional(readOnly = true)
    public String branchName(UUID branchId) {
        if (branchId == null) {
            return null;
        }
        List<String> names = jdbc.queryForList("SELECT name FROM location.branches WHERE id = :id",
                new MapSqlParameterSource("id", branchId), String.class);
        return names.isEmpty() ? branchId.toString() : names.get(0);
    }

    @Transactional(readOnly = true)
    public ReportDataDTO page(String type, ReportFilter filter, int page, int size, String staffEmail) {
        if (page < 0) {
            throw ReportQueryCatalog.bad("La página debe ser 0 o mayor.");
        }
        if (size < 1 || size > maxPageSize) {
            throw ReportQueryCatalog.bad("El tamaño de página debe estar entre 1 y " + maxPageSize + ".");
        }
        return run(type, scoped(type, filter, staffEmail), page, size);
    }

    /** Todas las filas del reporte para exportar; supera el límite configurado -> se pide acotar los filtros. */
    @Transactional(readOnly = true)
    public ReportDataDTO exportAll(String type, ReportFilter filter, String staffEmail) {
        ReportDataDTO first = run(type, scoped(type, filter, staffEmail), 0, 1);
        if (first.totalElements() > maxExportRows) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "El reporte tiene " + first.totalElements() + " filas y el máximo exportable es " + maxExportRows
                            + ". Acota el periodo o la sucursal.");
        }
        return run(type, scoped(type, filter, staffEmail), 0, (int) Math.max(1, first.totalElements()));
    }

    private ReportFilter scoped(String type, ReportFilter filter, String staffEmail) {
        Optional<UUID> forced = scope.forStaff(staffEmail);
        if (forced.isEmpty()) {
            return filter;
        }
        if ("USERS".equalsIgnoreCase(type)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "El reporte de usuarios solo está disponible para el administrador global.");
        }
        if (filter.branchId() != null && !filter.branchId().equals(forced.get())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes consultar los reportes de tu sucursal.");
        }
        return filter.withBranch(forced.get());
    }

    private ReportDataDTO run(String type, ReportFilter filter, int page, int size) {
        String code = type == null ? "" : type.trim().toUpperCase();
        if (!TYPES.contains(code)) {
            throw ReportQueryCatalog.bad("Tipo de reporte inválido: " + type);
        }
        validateRange(filter);

        MapSqlParameterSource params = new MapSqlParameterSource();
        Spec spec = ReportQueryCatalog.resolve(code, filter, params);

        Long total = jdbc.queryForObject("SELECT count(*) FROM (" + spec.inner() + ") t", params, Long.class);
        long totalElements = total == null ? 0 : total;

        MapSqlParameterSource pageParams = new MapSqlParameterSource(params.getValues());
        pageParams.addValue("limit", size).addValue("offset", (long) page * size);
        List<Map<String, Object>> raw = jdbc.queryForList(
                "SELECT * FROM (" + spec.inner() + ") t ORDER BY " + spec.order() + " LIMIT :limit OFFSET :offset", pageParams);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> clean = new LinkedHashMap<>();
            for (Column column : spec.columns()) {
                clean.put(column.key(), normalize(row.get(column.key())));
            }
            rows.add(clean);
        }

        StringBuilder metricSql = new StringBuilder("SELECT ");
        for (int i = 0; i < spec.metrics().size(); i++) {
            metricSql.append(i == 0 ? "" : ", ").append(spec.metrics().get(i).sql()).append(" AS m").append(i);
        }
        metricSql.append(" FROM (").append(spec.inner()).append(") t");
        Map<String, Object> values = jdbc.queryForMap(metricSql.toString(), params);
        List<Metric> summary = new ArrayList<>();
        for (int i = 0; i < spec.metrics().size(); i++) {
            MetricDef def = spec.metrics().get(i);
            summary.add(new Metric(def.key(), def.label(), normalize(values.get("m" + i)), def.kind()));
        }

        Map<String, Object> applied = new LinkedHashMap<>();
        applied.put("from", filter.from());
        applied.put("to", filter.to());
        applied.put("branchId", filter.branchId());
        applied.put("status", filter.status());
        applied.put("category", filter.category());
        applied.put("priority", filter.priority());
        applied.put("search", filter.search());
        int totalPages = (int) Math.ceil(totalElements / (double) size);
        return new ReportDataDTO(code, spec.title(), spec.columns(), rows, summary, page, size, totalElements, totalPages,
                applied, Instant.now());
    }

    private void validateRange(ReportFilter f) {
        if (f.from() != null && f.to() != null) {
            if (f.from().isAfter(f.to())) {
                throw ReportQueryCatalog.bad("La fecha inicial no puede ser posterior a la final.");
            }
            if (ChronoUnit.DAYS.between(f.from(), f.to()) > maxRangeDays) {
                throw ReportQueryCatalog.bad("El periodo no puede superar " + maxRangeDays + " días.");
            }
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(ReportQueryCatalog.ZONE).toOffsetDateTime().toString();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(ReportQueryCatalog.ZONE).toOffsetDateTime().toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().scale() <= 0 ? (Object) bd.toBigInteger().longValue() : bd.doubleValue();
        }
        return value;
    }
}
