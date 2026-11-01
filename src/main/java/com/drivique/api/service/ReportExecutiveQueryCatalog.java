package com.drivique.api.service;

import com.drivique.api.dto.ReportDataDTO.Column;
import com.drivique.api.service.ReportDataService.ReportFilter;
import com.drivique.api.service.ReportQueryCatalog.MetricDef;
import com.drivique.api.service.ReportQueryCatalog.Spec;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public final class ReportExecutiveQueryCatalog {

    private ReportExecutiveQueryCatalog() {
    }

    static Spec users(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE u.deleted_at IS NULL");
        ReportQueryCatalog.dateRange(w, p, "u.created_at", f);
        ReportQueryCatalog.search(w, p, f, "u.first_name || ' ' || u.last_name || ' ' || u.email || ' ' || coalesce(u.document_number, '')");
        if (ReportQueryCatalog.notBlank(f.status())) {
            w.append(" AND u.account_status IN (:status)");
            p.addValue("status", ReportQueryCatalog.values(f.status()));
        }
        if (ReportQueryCatalog.notBlank(f.category())) {
            w.append(" AND EXISTS (SELECT 1 FROM iam.user_roles ur2 JOIN iam.roles r2 ON r2.id = ur2.role_id "
                    + "WHERE ur2.user_id = u.id AND r2.code IN (:role))");
            p.addValue("role", ReportQueryCatalog.values(f.category()));
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

    static Spec promotions(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        ReportQueryCatalog.dateRange(w, p, "pr.created_at", f);
        ReportQueryCatalog.search(w, p, f, "pr.code || ' ' || coalesce(pr.name, '')");
        if (ReportQueryCatalog.notBlank(f.status())) {
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

    static Spec executive(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder res = new StringBuilder();
        StringBuilder pay = new StringBuilder();
        StringBuilder inc = new StringBuilder();
        StringBuilder con = new StringBuilder();
        if (f.from() != null) {
            p.addValue("fromTs", f.from().atStartOfDay(ReportQueryCatalog.ZONE).toOffsetDateTime());
            res.append(" AND r.created_at >= :fromTs");
            pay.append(" AND coalesce(p.paid_at, p.created_at) >= :fromTs");
            inc.append(" AND i.created_at >= :fromTs");
            con.append(" AND ct.created_at >= :fromTs");
        }
        if (f.to() != null) {
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ReportQueryCatalog.ZONE).toOffsetDateTime());
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

    static Spec incidentsMaintenance(ReportFilter f, MapSqlParameterSource p) {
        StringBuilder inc = new StringBuilder(" WHERE 1 = 1");
        StringBuilder man = new StringBuilder(" WHERE 1 = 1");
        if (f.from() != null) {
            p.addValue("fromTs", f.from().atStartOfDay(ReportQueryCatalog.ZONE).toOffsetDateTime());
            p.addValue("fromDate", f.from());
            inc.append(" AND i.created_at >= :fromTs");
            man.append(" AND coalesce(m.completed_date, m.scheduled_date) >= :fromDate");
        }
        if (f.to() != null) {
            p.addValue("toTs", f.to().plusDays(1).atStartOfDay(ReportQueryCatalog.ZONE).toOffsetDateTime());
            p.addValue("toDate", f.to());
            inc.append(" AND i.created_at < :toTs");
            man.append(" AND coalesce(m.completed_date, m.scheduled_date) <= :toDate");
        }
        if (f.branchId() != null) {
            p.addValue("branchId", f.branchId());
            inc.append(" AND i.branch_id = :branchId");
            man.append(" AND v.current_branch_id = :branchId");
        }
        boolean incidentOnly = ReportQueryCatalog.notBlank(f.status()) || ReportQueryCatalog.notBlank(f.priority());
        if (ReportQueryCatalog.notBlank(f.status())) {
            inc.append(" AND i.status IN (:status)");
            p.addValue("status", ReportQueryCatalog.values(f.status()));
        }
        if (ReportQueryCatalog.notBlank(f.priority())) {
            inc.append(" AND i.priority IN (:priority)");
            p.addValue("priority", ReportQueryCatalog.values(f.priority()));
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
}

