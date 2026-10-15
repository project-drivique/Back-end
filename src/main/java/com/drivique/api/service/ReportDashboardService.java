package com.drivique.api.service;

import com.drivique.api.dto.ReportDashboardDTO;
import com.drivique.api.dto.ReportDashboardDTO.BranchRow;
import com.drivique.api.dto.ReportDashboardDTO.Fleet;
import com.drivique.api.dto.ReportDashboardDTO.MonthRevenue;
import com.drivique.api.dto.ReportDashboardDTO.Payments;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cifras del tablero de reportes calculadas en la base de datos; un encargado solo ve su sucursal. */
@Service
public class ReportDashboardService {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final String[] MONTHS = {"Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"};
    private static final String NOT_CANCELLED = "st.code NOT IN ('CANCELLED_BY_TIMEOUT','CANCELLED_BY_USER','REJECTED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final StaffBranchScope scope;

    public ReportDashboardService(NamedParameterJdbcTemplate jdbc, StaffBranchScope scope) {
        this.jdbc = jdbc;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public ReportDashboardDTO dashboard(String staffEmail) {
        Optional<UUID> branch = scope.forStaff(staffEmail);
        MapSqlParameterSource p = new MapSqlParameterSource();
        String vehicleScope = "";
        String branchScope = "";
        if (branch.isPresent()) {
            p.addValue("branchId", branch.get());
            vehicleScope = " AND v.current_branch_id = :branchId";
            branchScope = " AND b.id = :branchId";
        }
        LocalDate today = LocalDate.now(ZONE);
        int year = today.getYear();
        p.addValue("year", year);

        // Reservas facturadas: con al menos un pago aprobado y que no fueron canceladas ni rechazadas
        String billed = "SELECT r.id AS reservation_id, v.current_branch_id AS branch_id, st.code AS status_code, "
                + "sum(pay.amount) AS amount, max(coalesce(pay.paid_at, pay.created_at)) AS paid_at "
                + "FROM billing.payments pay "
                + "JOIN billing.payment_statuses ps ON ps.id = pay.status_id AND ps.code = 'APPROVED' "
                + "JOIN contract.rental_contracts c ON c.id = pay.contract_id "
                + "JOIN rental.reservations r ON r.id = c.reservation_id "
                + "JOIN rental.reservation_statuses st ON st.id = r.status_id AND " + NOT_CANCELLED + " "
                + "JOIN fleet.vehicles v ON v.id = r.vehicle_id WHERE 1 = 1" + vehicleScope
                + " GROUP BY r.id, v.current_branch_id, st.code";

        Map<String, Object> totals = jdbc.queryForMap("SELECT coalesce(sum(amount), 0) AS revenue, count(*) AS billed, "
                + "count(*) FILTER (WHERE status_code = 'COMPLETED') AS completed FROM (" + billed + ") b", p);
        long revenue = number(totals.get("revenue"));
        long billedCount = number(totals.get("billed"));
        long completed = number(totals.get("completed"));

        long[] perMonth = new long[12];
        for (Map<String, Object> row : jdbc.queryForList("SELECT extract(month FROM paid_at AT TIME ZONE 'America/Bogota')::int AS m, "
                + "sum(amount) AS total FROM (" + billed + ") b WHERE extract(year FROM paid_at AT TIME ZONE 'America/Bogota') = :year GROUP BY 1", p)) {
            perMonth[((Number) row.get("m")).intValue() - 1] = number(row.get("total"));
        }
        List<MonthRevenue> months = new ArrayList<>();
        for (int i = 0; i < today.getMonthValue(); i++) {
            months.add(new MonthRevenue(MONTHS[i], perMonth[i]));
        }
        long current = perMonth[today.getMonthValue() - 1];
        long previous = today.getMonthValue() > 1 ? perMonth[today.getMonthValue() - 2] : monthTotal(billed, p, year - 1, 12);
        Integer variation = previous > 0 ? (int) Math.round((current - previous) * 100.0 / previous) : null;

        // Flota: alquilada = estado RENTED o con una reserva en curso hoy
        String rentedNow = "(vs.code = 'RENTED' OR EXISTS (SELECT 1 FROM rental.reservations rr WHERE rr.vehicle_id = v.id "
                + "AND rr.blocks_availability AND rr.pickup_date <= now() AND rr.return_date > now()))";
        Map<String, Object> fleetRow = jdbc.queryForMap("SELECT count(*) AS total, count(*) FILTER (WHERE " + rentedNow + ") AS rented, "
                + "count(*) FILTER (WHERE vs.code = 'MAINTENANCE') AS maintenance, "
                + "count(*) FILTER (WHERE vs.code = 'AVAILABLE' AND NOT " + rentedNow + ") AS available "
                + "FROM fleet.vehicles v JOIN fleet.vehicle_statuses vs ON vs.id = v.status_id WHERE v.is_active" + vehicleScope, p);
        long fleetTotal = number(fleetRow.get("total"));
        long rented = number(fleetRow.get("rented"));
        long available = number(fleetRow.get("available"));
        long maintenance = number(fleetRow.get("maintenance"));
        Fleet fleet = new Fleet(fleetTotal, rented, available, maintenance, pct(rented, fleetTotal), pct(available, fleetTotal),
                pct(maintenance, fleetTotal));

        Map<String, Object> pay = jdbc.queryForMap("SELECT count(*) FILTER (WHERE r.cash_payment_code IS NOT NULL) AS cash, "
                + "count(*) FILTER (WHERE r.cash_payment_code IS NULL) AS wompi "
                + "FROM rental.reservations r JOIN rental.reservation_statuses st ON st.id = r.status_id AND " + NOT_CANCELLED
                + " JOIN fleet.vehicles v ON v.id = r.vehicle_id WHERE 1 = 1" + vehicleScope, p);
        long cash = number(pay.get("cash"));
        long wompi = number(pay.get("wompi"));
        Payments payments = new Payments(cash + wompi, pct(wompi, cash + wompi), pct(cash, cash + wompi));

        Map<String, Object> reviews = jdbc.queryForMap("SELECT count(*) AS total, coalesce(avg(br.rating), 0) AS average "
                + "FROM rental.branch_reviews br JOIN location.branches b ON b.id = br.branch_id WHERE 1 = 1" + branchScope, p);

        List<BranchRow> rows = new ArrayList<>();
        String branchRows = "SELECT b.id, b.name, coalesce(c.name, '—') AS city, "
                + "(SELECT trim(coalesce(vb.name, '') || ' ' || v2.model) FROM rental.reservations r2 JOIN fleet.vehicles v2 ON v2.id = r2.vehicle_id "
                + "LEFT JOIN fleet.vehicle_brands vb ON vb.id = v2.brand_id WHERE v2.current_branch_id = b.id "
                + "GROUP BY vb.name, v2.model ORDER BY count(*) DESC, 1 LIMIT 1) AS top_vehicle, "
                + "(SELECT count(*) FROM rental.reservations r3 JOIN fleet.vehicles v3 ON v3.id = r3.vehicle_id WHERE v3.current_branch_id = b.id) AS reservations, "
                + "(SELECT coalesce(sum(greatest(1, ceil(extract(epoch FROM (r4.return_date - r4.pickup_date)) / 86400.0))), 0) "
                + "FROM rental.reservations r4 JOIN rental.reservation_statuses st ON st.id = r4.status_id AND " + NOT_CANCELLED
                + " JOIN fleet.vehicles v4 ON v4.id = r4.vehicle_id WHERE v4.current_branch_id = b.id) AS days, "
                + "(SELECT coalesce(sum(amount), 0) FROM (" + billed.replace("WHERE 1 = 1" + vehicleScope, "WHERE 1 = 1") + ") bb WHERE bb.branch_id = b.id) AS revenue, "
                + "(SELECT count(*) FROM fleet.vehicles v5 JOIN fleet.vehicle_statuses vs ON vs.id = v5.status_id WHERE v5.is_active AND v5.current_branch_id = b.id "
                + "AND (vs.code = 'RENTED' OR EXISTS (SELECT 1 FROM rental.reservations rr WHERE rr.vehicle_id = v5.id AND rr.blocks_availability "
                + "AND rr.pickup_date <= now() AND rr.return_date > now()))) AS rented, "
                + "(SELECT count(*) FROM fleet.vehicles v6 WHERE v6.is_active AND v6.current_branch_id = b.id) AS vehicles, "
                + "(SELECT count(*) FROM support.incident_reports i WHERE i.branch_id = b.id) AS incidents, "
                + "(SELECT coalesce(avg(br.rating), 0) FROM rental.branch_reviews br WHERE br.branch_id = b.id) AS rating, "
                + "(SELECT count(*) FROM rental.branch_reviews br2 WHERE br2.branch_id = b.id) AS reviews "
                + "FROM location.branches b LEFT JOIN location.cities c ON c.id = b.city_id WHERE b.is_active" + branchScope
                + " ORDER BY revenue DESC, b.name";
        for (Map<String, Object> r : jdbc.queryForList(branchRows, p)) {
            long vehicles = number(r.get("vehicles"));
            rows.add(new BranchRow((UUID) r.get("id"), (String) r.get("name"), (String) r.get("city"),
                    r.get("top_vehicle") == null ? "" : (String) r.get("top_vehicle"), number(r.get("reservations")),
                    number(r.get("days")), number(r.get("revenue")), pct(number(r.get("rented")), vehicles),
                    number(r.get("incidents")), decimal(r.get("rating")), number(r.get("reviews"))));
        }
        return new ReportDashboardDTO(year, revenue, variation, billedCount, pct(completed, billedCount), pct(rented, fleetTotal),
                decimal(reviews.get("average")), number(reviews.get("total")), months, fleet, payments, rows);
    }

    private long monthTotal(String billed, MapSqlParameterSource p, int year, int month) {
        MapSqlParameterSource q = new MapSqlParameterSource(p.getValues()).addValue("y", year).addValue("m", month);
        Number n = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM (" + billed + ") b "
                + "WHERE extract(year FROM paid_at AT TIME ZONE 'America/Bogota') = :y AND extract(month FROM paid_at AT TIME ZONE 'America/Bogota') = :m", q, Number.class);
        return n == null ? 0 : n.longValue();
    }

    private static long number(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue()) : 0;
    }

    private static double decimal(Object value) {
        return value instanceof Number n ? Math.round(n.doubleValue() * 100.0) / 100.0 : 0;
    }

    private static int pct(long part, long total) {
        return total > 0 ? (int) Math.round(part * 100.0 / total) : 0;
    }
}
