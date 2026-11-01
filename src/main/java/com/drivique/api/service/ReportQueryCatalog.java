package com.drivique.api.service;

import com.drivique.api.dto.ReportDataDTO.Column;
import com.drivique.api.service.ReportDataService.ReportFilter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.web.server.ResponseStatusException;

public final class ReportQueryCatalog {

    static final ZoneId ZONE = ZoneId.of("America/Bogota");

    record MetricDef(String key, String label, String kind, String sql) {}

    record Spec(String title, List<Column> columns, String inner, String order, List<MetricDef> metrics) {}

    private ReportQueryCatalog() {
    }

    static Spec resolve(String code, ReportFilter filter, MapSqlParameterSource params) {
        return switch (code) {
            case "RESERVATIONS" -> reservations(filter, params);
            case "REVENUE_SUMMARY" -> revenue(filter, params);
            case "FLEET_OCCUPANCY" -> occupancy(filter, params);
            case "VEHICLES" -> vehicles(filter, params);
            case "BRANCHES" -> branches(filter, params);
            case "CONTRACTS" -> contracts(filter, params);
            case "USERS" -> ReportExecutiveQueryCatalog.users(filter, params);
            case "PROMOTIONS" -> ReportExecutiveQueryCatalog.promotions(filter, params);
            case "EXECUTIVE_SUMMARY" -> ReportExecutiveQueryCatalog.executive(filter, params);
            case "INCIDENTS_MAINTENANCE" -> ReportExecutiveQueryCatalog.incidentsMaintenance(filter, params);
            default -> incidents(filter, params);
        };
    }

    private static Spec reservations(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "r.created_at", f);
        search(w, p, f, "r.code || ' ' || u.first_name || ' ' || u.last_name || ' ' || v.plate");
        if (f.branchId() != null) {
            w.append(" AND (v.current_branch_id = :branchId OR r.cash_payment_branch_id = :branchId)");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND s.code IN (:status)");
            p.addValue("status", values(f.status()));
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

    private static Spec revenue(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "coalesce(p.paid_at, p.created_at)", f);
        search(w, p, f, "coalesce(r.code, '') || ' ' || coalesce(u.first_name, '') || ' ' || coalesce(u.last_name, '') || ' ' || pm.name");
        if (f.branchId() != null) {
            w.append(" AND c.pickup_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND pm.code IN (:status)");
            p.addValue("status", values(f.status()));
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

    private static Spec occupancy(ReportFilter f, MapSqlParameterSource p) {
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
            w.append(" AND vs.code IN (:status)");
            p.addValue("status", values(f.status()));
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

    private static Spec vehicles(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE v.is_active");
        search(w, p, f, "v.plate || ' ' || v.model || ' ' || coalesce(vb.name, '')");
        if (f.branchId() != null) {
            w.append(" AND v.current_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND vs.code IN (:status)");
            p.addValue("status", values(f.status()));
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

    private static Spec branches(ReportFilter f, MapSqlParameterSource p) {
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

    private static Spec incidents(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "i.created_at", f);
        search(w, p, f, "i.incident_code || ' ' || i.subject");
        if (f.branchId() != null) {
            w.append(" AND i.branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND i.status IN (:status)");
            p.addValue("status", values(f.status()));
        }
        if (notBlank(f.priority())) {
            w.append(" AND i.priority IN (:priority)");
            p.addValue("priority", values(f.priority()));
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

    private static Spec contracts(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        dateRange(w, p, "ct.created_at", f);
        search(w, p, f, "ct.contract_number || ' ' || u.first_name || ' ' || u.last_name || ' ' || coalesce(r.code, '')");
        if (f.branchId() != null) {
            w.append(" AND ct.pickup_branch_id = :branchId");
            p.addValue("branchId", f.branchId());
        }
        if (notBlank(f.status())) {
            w.append(" AND cs.code IN (:status)");
            p.addValue("status", values(f.status()));
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

    static void search(StringBuilder where, MapSqlParameterSource p, ReportFilter f, String expression) {
        if (f.search() != null && !f.search().isBlank()) {
            where.append(" AND (").append(expression).append(") ILIKE :search ESCAPE '!'");
            p.addValue("search", "%" + f.search().trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
    }

    static void dateRange(StringBuilder where, MapSqlParameterSource p, String column, ReportFilter f) {
        if (f.from() != null) {
            where.append(" AND ").append(column).append(" >= :fromTs");
            p.addValue("fromTs", f.from().atStartOfDay(ZONE).toOffsetDateTime());
        }
        if (f.to() != null) {
            where.append(" AND ").append(column).append(" < :toTs");
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        }
    }

    static List<String> values(String raw) {
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim().toUpperCase());
            }
        }
        return out;
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank() && !"all".equalsIgnoreCase(value.trim());
    }

    static String uuid(String value, String message) {
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw bad(message);
        }
    }

    static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
