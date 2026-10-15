package com.drivique.api.service;

import com.drivique.api.dto.ReportDataDTO;
import com.drivique.api.dto.ReportDataDTO.Column;
import com.drivique.api.dto.ReportDataDTO.Metric;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");

    /** Filtros comunes; cada reporte usa los que le aplican. */
    public record ReportFilter(LocalDate from, LocalDate to, UUID branchId, String status, String category, String priority,
                          String search) {
        ReportFilter withBranch(UUID branch) {
            return new ReportFilter(from, to, branch, status, category, priority, search);
        }
    }

    private record MetricDef(String key, String label, String kind, String sql) {}

    private record Spec(String title, List<Column> columns, String inner, String order, List<MetricDef> metrics) {}

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
            throw bad("La página debe ser 0 o mayor.");
        }
        if (size < 1 || size > maxPageSize) {
            throw bad("El tamaño de página debe estar entre 1 y " + maxPageSize + ".");
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
            throw bad("Tipo de reporte inválido: " + type);
        }
        validateRange(filter);

        MapSqlParameterSource params = new MapSqlParameterSource();
        Spec spec = switch (code) {
            case "RESERVATIONS" -> reservations(filter, params);
            case "REVENUE_SUMMARY" -> revenue(filter, params);
            case "FLEET_OCCUPANCY" -> occupancy(filter, params);
            case "VEHICLES" -> vehicles(filter, params);
            case "BRANCHES" -> branches(filter, params);
            case "CONTRACTS" -> contracts(filter, params);
            case "USERS" -> users(filter, params);
            case "PROMOTIONS" -> promotions(filter, params);
            case "EXECUTIVE_SUMMARY" -> executive(filter, params);
            case "INCIDENTS_MAINTENANCE" -> incidentsMaintenance(filter, params);
            default -> incidents(filter, params);
        };

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
                throw bad("La fecha inicial no puede ser posterior a la final.");
            }
            if (ChronoUnit.DAYS.between(f.from(), f.to()) > maxRangeDays) {
                throw bad("El periodo no puede superar " + maxRangeDays + " días.");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Definición de cada reporte
    // ---------------------------------------------------------------------------------------------

    private Spec reservations(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "r.created_at", f);
        search(w, p, f, "r.code || ' ' || u.first_name || ' ' || u.last_name || ' ' || v.plate");
        if (f.branchId() != null) {
            w.append(" AND (v.current_branch_id = :branchId OR r.cash_payment_branch_id = :branchId)");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND s.code = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        String inner = "SELECT r.code AS code, trim(u.first_name || ' ' || u.last_name) AS customer, "
                + "trim(coalesce(vb.name, '') || ' ' || v.model) AS vehicle, coalesce(b.name, '—') AS branch, "
                + "r.pickup_date AS pickup, r.return_date AS return_at, s.name AS status, s.code AS status_code, "
                + "r.total_estimated AS total, r.created_at AS created_at "
                + "FROM rental.reservations r JOIN rental.reservation_statuses s ON s.id = r.status_id "
                + "JOIN iam.users u ON u.id = r.customer_id JOIN fleet.vehicles v ON v.id = r.vehicle_id "
                + "LEFT JOIN fleet.vehicle_brands vb ON vb.id = v.brand_id "
                + "LEFT JOIN location.branches b ON b.id = v.current_branch_id" + w;
        return new Spec("Reporte de reservas", List.of(
                new Column("code", "Código", "text"), new Column("customer", "Cliente", "text"),
                new Column("vehicle", "Vehículo", "text"), new Column("branch", "Sucursal", "text"),
                new Column("pickup", "Recogida", "date"), new Column("return_at", "Devolución", "date"),
                new Column("status", "Estado", "text"), new Column("total", "Total", "money"),
                new Column("created_at", "Creada", "date")),
                inner, "created_at DESC, code", List.of(
                new MetricDef("total", "Reservas", "number", "count(*)"),
                new MetricDef("confirmed", "Confirmadas o en curso", "number",
                        "count(*) FILTER (WHERE status_code IN ('CONFIRMED','IN_PROGRESS','COMPLETED'))"),
                new MetricDef("cancelled", "Canceladas o rechazadas", "number",
                        "count(*) FILTER (WHERE status_code IN ('CANCELLED_BY_TIMEOUT','CANCELLED_BY_USER','REJECTED'))"),
                new MetricDef("value", "Valor total", "money", "coalesce(sum(total), 0)"),
                new MetricDef("average", "Ticket promedio", "money", "coalesce(round(avg(total), 0), 0)")));
    }

    private Spec revenue(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "coalesce(p.paid_at, p.created_at)", f);
        search(w, p, f, "coalesce(r.code, '') || ' ' || coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '') || ' ' || pm.name");
        if (f.branchId() != null) {
            w.append(" AND c.pickup_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND pm.code = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        String inner = "SELECT coalesce(p.paid_at, p.created_at) AS paid_at, coalesce(r.code, '—') AS reservation, "
                + "coalesce(trim(u.first_name || ' ' || u.last_name), '—') AS customer, pm.name AS method, "
                + "coalesce(b.name, '—') AS branch, p.amount AS amount, coalesce(cu.code, 'COP') AS currency "
                + "FROM billing.payments p JOIN billing.payment_statuses ps ON ps.id = p.status_id AND ps.code = 'APPROVED' "
                + "JOIN billing.payment_methods pm ON pm.id = p.payment_method_id "
                + "LEFT JOIN core.currencies cu ON cu.id = p.currency_id "
                + "LEFT JOIN contract.rental_contracts c ON c.id = p.contract_id "
                + "LEFT JOIN rental.reservations r ON r.id = c.reservation_id "
                + "LEFT JOIN iam.users u ON u.id = c.customer_id "
                + "LEFT JOIN location.branches b ON b.id = c.pickup_branch_id" + w;
        return new Spec("Reporte de ingresos", List.of(
                new Column("paid_at", "Fecha de pago", "date"), new Column("reservation", "Reserva", "text"),
                new Column("customer", "Cliente", "text"), new Column("method", "Método de pago", "text"),
                new Column("branch", "Sucursal", "text"), new Column("amount", "Monto", "money"),
                new Column("currency", "Moneda", "text")),
                inner, "paid_at DESC, reservation", List.of(
                new MetricDef("payments", "Pagos aprobados", "number", "count(*)"),
                new MetricDef("total", "Ingresos (COP)", "money", "coalesce(sum(amount) FILTER (WHERE currency = 'COP'), 0)"),
                new MetricDef("average", "Pago promedio (COP)", "money",
                        "coalesce(round(avg(amount) FILTER (WHERE currency = 'COP'), 0), 0)"),
                new MetricDef("largest", "Pago más alto (COP)", "money",
                        "coalesce(max(amount) FILTER (WHERE currency = 'COP'), 0)")));
    }

    private Spec occupancy(ReportFilter f, MapSqlParameterSource p) {
        LocalDate to = f.to() != null ? f.to() : LocalDate.now(ZONE);
        LocalDate from = f.from() != null ? f.from() : to.minusDays(29);
        if (from.isAfter(to)) {
            throw bad("La fecha inicial no puede ser posterior a la final.");
        }
        p.addValue("fromTs", from.atStartOfDay(ZONE).toOffsetDateTime());
        p.addValue("toTs", to.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        p.addValue("periodDays", BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1));
        StringBuilder w = new StringBuilder(" WHERE v.is_active");
        if (f.branchId() != null) {
            w.append(" AND v.current_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND vs.code = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        String overlap = "greatest(0, extract(epoch FROM (least(r.return_date, :toTs) - greatest(r.pickup_date, :fromTs))) / 86400.0)";
        String inner = "SELECT v.plate AS plate, trim(coalesce(vb.name, '') || ' ' || v.model) AS vehicle, "
                + "coalesce(b.name, '—') AS branch, vs.name AS status, "
                + "round(coalesce(sum(" + overlap + "), 0)::numeric, 1) AS days_rented, "
                + "least(100, round(coalesce(sum(" + overlap + "), 0)::numeric / :periodDays * 100, 1)) AS occupancy "
                + "FROM fleet.vehicles v JOIN fleet.vehicle_statuses vs ON vs.id = v.status_id "
                + "LEFT JOIN fleet.vehicle_brands vb ON vb.id = v.brand_id "
                + "LEFT JOIN location.branches b ON b.id = v.current_branch_id "
                + "LEFT JOIN rental.reservations r ON r.vehicle_id = v.id AND r.blocks_availability "
                + "AND r.pickup_date < :toTs AND r.return_date > :fromTs" + w
                + " GROUP BY v.id, vb.name, b.name, vs.name";
        return new Spec("Ocupación de flota (" + from + " al " + to + ")", List.of(
                new Column("plate", "Placa", "text"), new Column("vehicle", "Vehículo", "text"),
                new Column("branch", "Sucursal", "text"), new Column("status", "Estado actual", "text"),
                new Column("days_rented", "Días alquilado", "number"), new Column("occupancy", "Ocupación", "percent")),
                inner, "occupancy DESC, plate", List.of(
                new MetricDef("vehicles", "Vehículos", "number", "count(*)"),
                new MetricDef("occupancy", "Ocupación promedio", "percent", "coalesce(round(avg(occupancy), 1), 0)"),
                new MetricDef("days", "Días alquilados", "number", "coalesce(sum(days_rented), 0)"),
                new MetricDef("idle", "Sin alquilar en el periodo", "number", "count(*) FILTER (WHERE days_rented = 0)")));
    }

    private Spec vehicles(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE v.is_active");
        search(w, p, f, "v.plate || ' ' || v.model || ' ' || coalesce(vb.name, '')");
        if (f.branchId() != null) {
            w.append(" AND v.current_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND vs.code = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        if (notBlank(f.category())) {
            w.append(" AND v.category_id = CAST(:category AS uuid)");
            p.addValue("category", uuid(f.category(), "La categoría no es válida."));
        }
        String inner = "SELECT v.plate AS plate, trim(coalesce(vb.name, '') || ' ' || v.model) AS vehicle, v.year AS year, "
                + "coalesce(vc.name, '—') AS category, coalesce(b.name, '—') AS branch, vs.name AS status, "
                + "vs.code AS status_code, v.mileage AS mileage, v.daily_rate AS daily_rate "
                + "FROM fleet.vehicles v JOIN fleet.vehicle_statuses vs ON vs.id = v.status_id "
                + "LEFT JOIN fleet.vehicle_brands vb ON vb.id = v.brand_id "
                + "LEFT JOIN fleet.vehicle_categories vc ON vc.id = v.category_id "
                + "LEFT JOIN location.branches b ON b.id = v.current_branch_id" + w;
        return new Spec("Reporte de flota", List.of(
                new Column("plate", "Placa", "text"), new Column("vehicle", "Vehículo", "text"),
                new Column("year", "Año", "text"), new Column("category", "Categoría", "text"),
                new Column("branch", "Sucursal", "text"), new Column("status", "Estado", "text"),
                new Column("mileage", "Kilometraje", "number"), new Column("daily_rate", "Tarifa diaria", "money")),
                inner, "plate", List.of(
                new MetricDef("total", "Vehículos", "number", "count(*)"),
                new MetricDef("available", "Disponibles", "number", "count(*) FILTER (WHERE status_code = 'AVAILABLE')"),
                new MetricDef("rented", "Alquilados", "number", "count(*) FILTER (WHERE status_code = 'RENTED')"),
                new MetricDef("maintenance", "En mantenimiento", "number", "count(*) FILTER (WHERE status_code = 'MAINTENANCE')"),
                new MetricDef("rate", "Tarifa diaria promedio", "money", "coalesce(round(avg(daily_rate), 0), 0)")));
    }

    private Spec branches(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder res = new StringBuilder();
        StringBuilder pay = new StringBuilder();
        StringBuilder inc = new StringBuilder();
        if (f.from() != null) {
            p.addValue("fromTs", f.from().atStartOfDay(ZONE).toOffsetDateTime());
            res.append(" AND r.created_at >= :fromTs");
            pay.append(" AND coalesce(p.paid_at, p.created_at) >= :fromTs");
            inc.append(" AND i.created_at >= :fromTs");
        }
        if (f.to() != null) {
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
            res.append(" AND r.created_at < :toTs");
            pay.append(" AND coalesce(p.paid_at, p.created_at) < :toTs");
            inc.append(" AND i.created_at < :toTs");
        }
        StringBuilder w = new StringBuilder(" WHERE b.is_active");
        if (f.branchId() != null) {
            w.append(" AND b.id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        String inner = "SELECT b.name AS branch, coalesce(c.name, '—') AS city, "
                + "(SELECT count(*) FROM fleet.vehicles v WHERE v.current_branch_id = b.id AND v.is_active) AS vehicles, "
                + "(SELECT count(*) FROM rental.reservations r JOIN fleet.vehicles v ON v.id = r.vehicle_id "
                + "WHERE v.current_branch_id = b.id" + res + ") AS reservations, "
                + "(SELECT coalesce(sum(p.amount), 0) FROM billing.payments p "
                + "JOIN billing.payment_statuses ps ON ps.id = p.status_id AND ps.code = 'APPROVED' "
                + "JOIN contract.rental_contracts ct ON ct.id = p.contract_id WHERE ct.pickup_branch_id = b.id" + pay + ") AS revenue, "
                + "(SELECT count(*) FROM support.incident_reports i WHERE i.branch_id = b.id" + inc + ") AS incidents, "
                + "(SELECT coalesce(round(avg(br.rating), 2), 0) FROM rental.branch_reviews br WHERE br.branch_id = b.id) AS rating "
                + "FROM location.branches b LEFT JOIN location.cities c ON c.id = b.city_id" + w;
        return new Spec("Reporte de sedes", List.of(
                new Column("branch", "Sucursal", "text"), new Column("city", "Ciudad", "text"),
                new Column("vehicles", "Vehículos", "number"), new Column("reservations", "Reservas", "number"),
                new Column("revenue", "Ingresos", "money"), new Column("incidents", "Incidencias", "number"),
                new Column("rating", "Calificación", "number")),
                inner, "revenue DESC, branch", List.of(
                new MetricDef("branches", "Sucursales", "number", "count(*)"),
                new MetricDef("vehicles", "Vehículos", "number", "coalesce(sum(vehicles), 0)"),
                new MetricDef("reservations", "Reservas", "number", "coalesce(sum(reservations), 0)"),
                new MetricDef("revenue", "Ingresos", "money", "coalesce(sum(revenue), 0)"),
                new MetricDef("incidents", "Incidencias", "number", "coalesce(sum(incidents), 0)")));
    }

    private Spec incidents(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "i.created_at", f);
        search(w, p, f, "i.incident_code || ' ' || i.subject");
        if (f.branchId() != null) {
            w.append(" AND i.branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND i.status = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        if (notBlank(f.priority())) {
            w.append(" AND i.priority = :priority");
            p.addValue("priority", f.priority().trim().toUpperCase());
        }
        if (notBlank(f.category())) {
            w.append(" AND i.category = :category");
            p.addValue("category", f.category().trim());
        }
        String inner = "SELECT i.incident_code AS code, i.subject AS subject, coalesce(i.category, '—') AS category, "
                + "i.priority AS priority, i.status AS status, coalesce(b.name, '—') AS branch, i.created_at AS created_at, "
                + "coalesce(i.resolved_at, i.closed_at) AS resolved_at, "
                + "round((extract(epoch FROM (coalesce(i.resolved_at, i.closed_at) - i.created_at)) / 3600.0)::numeric, 1) AS hours "
                + "FROM support.incident_reports i LEFT JOIN location.branches b ON b.id = i.branch_id" + w;
        return new Spec("Reporte de incidencias", List.of(
                new Column("code", "Código", "text"), new Column("subject", "Asunto", "text"),
                new Column("category", "Categoría", "text"), new Column("priority", "Prioridad", "text"),
                new Column("status", "Estado", "text"), new Column("branch", "Sucursal", "text"),
                new Column("created_at", "Reportada", "date"), new Column("resolved_at", "Resuelta", "date"),
                new Column("hours", "Horas de resolución", "number")),
                inner, "created_at DESC, code", List.of(
                new MetricDef("total", "Incidencias", "number", "count(*)"),
                new MetricDef("open", "Abiertas", "number", "count(*) FILTER (WHERE status NOT IN ('RESOLVED','CLOSED'))"),
                new MetricDef("resolved", "Resueltas o cerradas", "number", "count(*) FILTER (WHERE status IN ('RESOLVED','CLOSED'))"),
                new MetricDef("high", "Prioridad alta", "number", "count(*) FILTER (WHERE priority = 'HIGH')"),
                new MetricDef("hours", "Horas promedio de resolución", "number", "coalesce(round(avg(hours), 1), 0)")));
    }

    private Spec contracts(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "ct.created_at", f);
        search(w, p, f, "ct.contract_number || ' ' || u.first_name || ' ' || u.last_name || ' ' || coalesce(r.code, '')");
        if (f.branchId() != null) {
            w.append(" AND ct.pickup_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND cs.code = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        String inner = "SELECT ct.contract_number AS number, coalesce(r.code, '—') AS reservation, "
                + "trim(u.first_name || ' ' || u.last_name) AS customer, trim(coalesce(vb.name, '') || ' ' || v.model) AS vehicle, "
                + "coalesce(b.name, '—') AS branch, cs.name AS status, ct.scheduled_start_at AS start_at, ct.scheduled_end_at AS end_at, "
                + "ct.signed_at AS signed_at, ct.base_amount AS amount, ct.created_at AS created_at "
                + "FROM contract.rental_contracts ct JOIN contract.contract_statuses cs ON cs.id = ct.status_id "
                + "JOIN iam.users u ON u.id = ct.customer_id JOIN fleet.vehicles v ON v.id = ct.vehicle_id "
                + "LEFT JOIN fleet.vehicle_brands vb ON vb.id = v.brand_id "
                + "LEFT JOIN rental.reservations r ON r.id = ct.reservation_id "
                + "LEFT JOIN location.branches b ON b.id = ct.pickup_branch_id" + w;
        return new Spec("Reporte de contratos", List.of(
                new Column("number", "Contrato", "text"), new Column("reservation", "Reserva", "text"),
                new Column("customer", "Cliente", "text"), new Column("vehicle", "Vehículo", "text"),
                new Column("branch", "Sucursal", "text"), new Column("status", "Estado", "text"),
                new Column("start_at", "Inicio", "date"), new Column("end_at", "Fin", "date"),
                new Column("signed_at", "Firmado", "date"), new Column("amount", "Valor base", "money")),
                inner, "created_at DESC, number", List.of(
                new MetricDef("total", "Contratos", "number", "count(*)"),
                new MetricDef("signed", "Firmados", "number", "count(*) FILTER (WHERE signed_at IS NOT NULL)"),
                new MetricDef("pending", "Pendientes de firma", "number", "count(*) FILTER (WHERE signed_at IS NULL)"),
                new MetricDef("value", "Valor total", "money", "coalesce(sum(amount), 0)")));
    }

    private Spec users(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE u.deleted_at IS NULL");
        dateRange(w, p, "u.created_at", f);
        search(w, p, f, "u.first_name || ' ' || u.last_name || ' ' || u.email || ' ' || coalesce(u.document_number, '')");
        if (notBlank(f.status())) {
            w.append(" AND u.account_status = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        if (notBlank(f.category())) {
            w.append(" AND EXISTS (SELECT 1 FROM iam.user_roles ur2 JOIN iam.roles r2 ON r2.id = ur2.role_id "
                    + "WHERE ur2.user_id = u.id AND r2.code = :role)");
            p.addValue("role", f.category().trim().toUpperCase());
        }
        String inner = "SELECT trim(u.first_name || ' ' || u.last_name) AS name, u.email AS email, coalesce(u.phone, '—') AS phone, "
                + "coalesce(string_agg(DISTINCT ro.name, ', '), '—') AS roles, u.account_status AS status, "
                + "CASE WHEN u.email_verified_at IS NULL THEN 'No' ELSE 'Sí' END AS verified, u.created_at AS created_at "
                + "FROM iam.users u LEFT JOIN iam.user_roles ur ON ur.user_id = u.id LEFT JOIN iam.roles ro ON ro.id = ur.role_id" + w
                + " GROUP BY u.id";
        return new Spec("Reporte de usuarios", List.of(
                new Column("name", "Nombre", "text"), new Column("email", "Correo", "text"), new Column("phone", "Teléfono", "text"),
                new Column("roles", "Roles", "text"), new Column("status", "Estado", "text"),
                new Column("verified", "Correo verificado", "text"), new Column("created_at", "Registro", "date")),
                inner, "created_at DESC, email", List.of(
                new MetricDef("total", "Usuarios", "number", "count(*)"),
                new MetricDef("active", "Activos", "number", "count(*) FILTER (WHERE status = 'ACTIVE')"),
                new MetricDef("verified", "Con correo verificado", "number", "count(*) FILTER (WHERE verified = 'Sí')")));
    }

    private Spec promotions(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "pr.created_at", f);
        search(w, p, f, "pr.code || ' ' || coalesce(pr.name, '')");
        if (notBlank(f.status())) {
            w.append(" AND pr.is_active = :active");
            p.addValue("active", "ACTIVE".equalsIgnoreCase(f.status().trim()));
        }
        String inner = "SELECT pr.code AS code, coalesce(pr.name, '—') AS name, pr.offer_type AS type, "
                + "CASE WHEN pr.discount_type = 'PERCENTAGE' THEN trim(to_char(pr.discount_value, 'FM990.99')) || '%' "
                + "ELSE '$' || trim(to_char(pr.discount_value, 'FM999G999G990')) END AS discount, "
                + "pr.starts_at AS starts_at, pr.ends_at AS ends_at, pr.current_uses_count AS uses, pr.max_uses_limit AS use_limit, "
                + "CASE WHEN pr.is_active THEN 'Activa' ELSE 'Inactiva' END AS status, pr.is_active AS active "
                + "FROM catalog.promotions pr" + w;
        return new Spec("Reporte de promociones y cupones", List.of(
                new Column("code", "Código", "text"), new Column("name", "Nombre", "text"), new Column("type", "Tipo", "text"),
                new Column("discount", "Descuento", "text"), new Column("starts_at", "Inicia", "date"),
                new Column("ends_at", "Termina", "date"), new Column("uses", "Usos", "number"),
                new Column("use_limit", "Límite", "number"), new Column("status", "Estado", "text")),
                inner, "starts_at DESC NULLS LAST, code", List.of(
                new MetricDef("total", "Promociones", "number", "count(*)"),
                new MetricDef("active", "Activas", "number", "count(*) FILTER (WHERE active)"),
                new MetricDef("uses", "Usos totales", "number", "coalesce(sum(uses), 0)")));
    }

    private Spec executive(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder res = new StringBuilder();
        StringBuilder pay = new StringBuilder();
        StringBuilder inc = new StringBuilder();
        StringBuilder con = new StringBuilder();
        if (f.from() != null) {
            p.addValue("fromTs", f.from().atStartOfDay(ZONE).toOffsetDateTime());
            res.append(" AND r.created_at >= :fromTs");
            pay.append(" AND coalesce(p.paid_at, p.created_at) >= :fromTs");
            inc.append(" AND i.created_at >= :fromTs");
            con.append(" AND ct.created_at >= :fromTs");
        }
        if (f.to() != null) {
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
            res.append(" AND r.created_at < :toTs");
            pay.append(" AND coalesce(p.paid_at, p.created_at) < :toTs");
            inc.append(" AND i.created_at < :toTs");
            con.append(" AND ct.created_at < :toTs");
        }
        String vehicleBranch = "";
        if (f.branchId() != null) {
            p.addValue("branchId", f.branchId());
            res.append(" AND v.current_branch_id = :branchId");
            pay.append(" AND ct.pickup_branch_id = :branchId");
            inc.append(" AND i.branch_id = :branchId");
            con.append(" AND ct.pickup_branch_id = :branchId");
            vehicleBranch = " AND v.current_branch_id = :branchId";
        }
        String rented = "(vs.code = 'RENTED' OR EXISTS (SELECT 1 FROM rental.reservations rr WHERE rr.vehicle_id = v.id "
                + "AND rr.blocks_availability AND rr.pickup_date <= now() AND rr.return_date > now()))";
        String inner = "SELECT * FROM (VALUES "
                + "(1, 'Ingresos aprobados (COP)', (SELECT coalesce(sum(p.amount), 0) FROM billing.payments p "
                + "JOIN billing.payment_statuses ps ON ps.id = p.status_id AND ps.code = 'APPROVED' "
                + "JOIN contract.rental_contracts ct ON ct.id = p.contract_id WHERE 1 = 1" + pay + "), 'Pagos aprobados en el periodo'), "
                + "(2, 'Reservas registradas', (SELECT count(*) FROM rental.reservations r JOIN fleet.vehicles v ON v.id = r.vehicle_id "
                + "WHERE 1 = 1" + res + ")::numeric, 'Todas las reservas creadas en el periodo'), "
                + "(3, 'Vehículos en flota', (SELECT count(*) FROM fleet.vehicles v WHERE v.is_active" + vehicleBranch + ")::numeric, 'Vehículos activos'), "
                + "(4, 'Ocupación de flota (%)', (SELECT coalesce(round(count(*) FILTER (WHERE " + rented + ") * 100.0 / nullif(count(*), 0), 1), 0) "
                + "FROM fleet.vehicles v JOIN fleet.vehicle_statuses vs ON vs.id = v.status_id WHERE v.is_active" + vehicleBranch
                + "), 'Vehículos alquilados hoy sobre la flota activa'), "
                + "(5, 'Incidencias reportadas', (SELECT count(*) FROM support.incident_reports i WHERE 1 = 1" + inc + ")::numeric, 'Incidencias creadas en el periodo'), "
                + "(6, 'Contratos generados', (SELECT count(*) FROM contract.rental_contracts ct WHERE 1 = 1" + con + ")::numeric, 'Contratos creados en el periodo')"
                + ") AS x(ord, indicator, value, detail)";
        return new Spec("Resumen ejecutivo y operativo", List.of(
                new Column("indicator", "Indicador", "text"), new Column("value", "Valor", "number"), new Column("detail", "Detalle", "text")),
                inner, "ord", List.of(
                new MetricDef("revenue", "Ingresos aprobados", "money", "coalesce(max(value) FILTER (WHERE ord = 1), 0)"),
                new MetricDef("reservations", "Reservas", "number", "coalesce(max(value) FILTER (WHERE ord = 2), 0)"),
                new MetricDef("occupancy", "Ocupación de flota", "percent", "coalesce(max(value) FILTER (WHERE ord = 4), 0)"),
                new MetricDef("incidents", "Incidencias", "number", "coalesce(max(value) FILTER (WHERE ord = 5), 0)")));
    }

    private Spec incidentsMaintenance(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder inc = new StringBuilder(" WHERE 1 = 1");
        StringBuilder man = new StringBuilder(" WHERE 1 = 1");
        if (f.from() != null) {
            p.addValue("fromTs", f.from().atStartOfDay(ZONE).toOffsetDateTime());
            p.addValue("fromDate", f.from());
            inc.append(" AND i.created_at >= :fromTs");
            man.append(" AND coalesce(m.completed_date, m.scheduled_date) >= :fromDate");
        }
        if (f.to() != null) {
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
            p.addValue("toDate", f.to());
            inc.append(" AND i.created_at < :toTs");
            man.append(" AND coalesce(m.completed_date, m.scheduled_date) <= :toDate");
        }
        if (f.branchId() != null) {
            p.addValue("branchId", f.branchId());
            inc.append(" AND i.branch_id = :branchId");
            man.append(" AND v.current_branch_id = :branchId");
        }
        boolean incidentOnly = notBlank(f.status()) || notBlank(f.priority());
        if (notBlank(f.status())) {
            inc.append(" AND i.status = :status");
            p.addValue("status", f.status().trim().toUpperCase());
        }
        if (notBlank(f.priority())) {
            inc.append(" AND i.priority = :priority");
            p.addValue("priority", f.priority().trim().toUpperCase());
        }
        if (incidentOnly) {
            man.append(" AND false");
        }
        String inner = "SELECT 'Incidencia' AS kind, i.incident_code AS reference, i.subject AS description, coalesce(b.name, '—') AS branch, "
                + "i.created_at AS date_at, i.status AS status, NULL::numeric AS cost "
                + "FROM support.incident_reports i LEFT JOIN location.branches b ON b.id = i.branch_id" + inc
                + " UNION ALL SELECT 'Mantenimiento', v.plate, trim(coalesce(mt.name, '') || ' ' || coalesce(m.description, '')), "
                + "coalesce(b.name, '—'), coalesce(m.completed_date, m.scheduled_date)::timestamp AT TIME ZONE 'America/Bogota', "
                + "CASE WHEN m.completed_date IS NULL THEN 'Programado' ELSE 'Completado' END, m.cost "
                + "FROM fleet.vehicle_maintenances m JOIN fleet.vehicles v ON v.id = m.vehicle_id "
                + "LEFT JOIN fleet.maintenance_types mt ON mt.id = m.maintenance_type_id "
                + "LEFT JOIN location.branches b ON b.id = v.current_branch_id" + man;
        return new Spec("Balance de incidencias y mantenimiento", List.of(
                new Column("kind", "Tipo", "text"), new Column("reference", "Referencia", "text"),
                new Column("description", "Descripción", "text"), new Column("branch", "Sucursal", "text"),
                new Column("date_at", "Fecha", "date"), new Column("status", "Estado", "text"), new Column("cost", "Costo", "money")),
                inner, "date_at DESC, reference", List.of(
                new MetricDef("incidents", "Incidencias", "number", "count(*) FILTER (WHERE kind = 'Incidencia')"),
                new MetricDef("maintenances", "Mantenimientos", "number", "count(*) FILTER (WHERE kind = 'Mantenimiento')"),
                new MetricDef("openIncidents", "Incidencias abiertas", "number",
                        "count(*) FILTER (WHERE kind = 'Incidencia' AND status NOT IN ('RESOLVED','CLOSED'))"),
                new MetricDef("cost", "Costo de mantenimiento", "money", "coalesce(sum(cost), 0)")));
    }

    private static void search(StringBuilder where, MapSqlParameterSource p, ReportFilter f, String expression) {
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" AND (").append(expression).append(") ILIKE :search ESCAPE '!'");
            p.addValue("search", "%" + f.search().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
    }


    // ---------------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------------

    private static void dateRange(StringBuilder where, MapSqlParameterSource p, String column, ReportFilter f) {
        if (f.from() != null) {
            where.append(" AND ").append(column).append(" >= :fromTs");
            p.addValue("fromTs", f.from().atStartOfDay(ZONE).toOffsetDateTime());
        }
        if (f.to() != null) {
            where.append(" AND ").append(column).append(" < :toTs");
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Timestamp ts) {
            return ts.toInstant().atZone(ZONE).toOffsetDateTime().toString();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.atZoneSameInstant(ZONE).toOffsetDateTime().toString();
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().scale() <= 0 ? (Object) bd.toBigInteger().longValue() : bd.doubleValue();
        }
        return value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank() && !"all".equalsIgnoreCase(value.trim());
    }

    private static String uuid(String value, String message) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw bad(message);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
