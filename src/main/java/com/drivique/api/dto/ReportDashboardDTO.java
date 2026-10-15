package com.drivique.api.dto;

import java.util.List;
import java.util.UUID;

/** Tablero de reportes calculado en el servidor (ingresos, flota, pagos y desempeño por sucursal). */
public record ReportDashboardDTO(
        int year,
        long revenue,
        Integer monthVariation,
        long billedReservations,
        int completionRate,
        int occupancy,
        double rating,
        long totalReviews,
        List<MonthRevenue> months,
        Fleet fleet,
        Payments payments,
        List<BranchRow> branches) {

    public record MonthRevenue(String label, long value) {}

    public record Fleet(long total, long rented, long available, long maintenance, int rentedPct, int availablePct, int maintenancePct) {}

    public record Payments(long total, int wompiPct, int cashPct) {}

    public record BranchRow(UUID id, String branch, String city, String topVehicle, long reservations, long days, long revenue,
                            int occupancy, long incidents, double rating, long reviews) {}
}
