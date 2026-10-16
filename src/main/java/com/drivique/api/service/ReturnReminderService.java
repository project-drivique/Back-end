package com.drivique.api.service;

import com.drivique.api.model.BranchUser;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.repository.ReservationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Avisos de devolución: el día antes de la fecha de devolución y cuando ya venció. Solo avisa; la reserva
 * se finaliza cuando el encargado registra la devolución.
 */
@Service
public class ReturnReminderService {

    private static final Logger LOG = LoggerFactory.getLogger(ReturnReminderService.class);
    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReservationRepository reservations;
    private final BranchUserRepository branchUsers;
    private final NotificationService notifications;

    public ReturnReminderService(ReservationRepository reservations, BranchUserRepository branchUsers,
                                 @Autowired(required = false) NotificationService notifications) {
        this.reservations = reservations;
        this.branchUsers = branchUsers;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.jobs.return-reminders.cron:0 0 9 * * *}", zone = "America/Bogota")
    public void scheduled() {
        try {
            LOG.info("Avisos de devolución enviados: {}", sendReminders());
        } catch (Exception e) {
            LOG.error("Error enviando avisos de devolución", e);
        }
    }

    /** Devuelve cuántos avisos nuevos se generaron (cada uno se envía una sola vez por día y reserva). */
    @Transactional
    public int sendReminders() {
        if (notifications == null) {
            return 0;
        }
        LocalDate today = LocalDate.now(ZONE);
        Instant from = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant to = today.plusDays(2).atStartOfDay(ZONE).toInstant();
        int sent = 0;
        for (Reservation r : reservations.findInProgressReturningBetween(from, to)) {
            sent += notifyCustomer(r, "RET-1D-" + r.getId(), "Tu devolución es mañana",
                    "Hola " + r.getCustomer().getFirstName() + ", mañana vence tu alquiler de " + vehicle(r) + " (reserva " + r.getCode()
                            + "). Devuelve el vehículo en la sucursal antes de " + WHEN.format(r.getReturnDate().atZone(ZONE)) + ".");
        }
        for (Reservation r : reservations.findInProgressOverdue(Instant.now())) {
            String day = today.toString().replace("-", "");
            sent += notifyCustomer(r, "RO-" + r.getId() + "-" + day, "Tu alquiler ya venció",
                    "Hola " + r.getCustomer().getFirstName() + ", la fecha de devolución de " + vehicle(r) + " (reserva " + r.getCode()
                            + ") fue " + WHEN.format(r.getReturnDate().atZone(ZONE)) + ". Devuélvelo lo antes posible a la sucursal para evitar recargos.");
            sent += notifyBranchStaff(r, "ROS-" + r.getId() + "-" + day, "Reserva vencida sin devolver",
                    "La reserva " + r.getCode() + " (" + vehicle(r) + ") debió devolverse el " + WHEN.format(r.getReturnDate().atZone(ZONE))
                            + " y sigue en curso.");
        }
        return sent;
    }

    private int notifyCustomer(Reservation r, String key, String subject, String message) {
        int sent = 0;
        Map<String, Object> meta = Map.of("actionPath", "/reservas", "actionLabel", "Ver mi reserva", "bookingCode", r.getCode());
        for (String channel : List.of("IN_APP", "EMAIL")) {
            try {
                if (notifications.sendWithMetadata(r.getCustomer(), channel, "RESERVATION", subject, message, r.getId(), meta, key + "-" + channel) != null) {
                    sent++;
                }
            } catch (Exception e) {
                LOG.warn("No se pudo enviar el aviso {} a la reserva {}: {}", key, r.getCode(), e.getMessage());
            }
        }
        return sent;
    }

    private int notifyBranchStaff(Reservation r, String key, String subject, String message) {
        if (r.getVehicle() == null || r.getVehicle().getCurrentBranch() == null) {
            return 0;
        }
        int sent = 0;
        Map<String, Object> meta = Map.of("actionPath", "/encargado/reservations", "actionLabel", "Ver reservas", "bookingCode", r.getCode());
        for (BranchUser assignment : branchUsers.findByBranchIdOrderByAssignedAtAsc(r.getVehicle().getCurrentBranch().getId())) {
            User staff = assignment.getUser();
            if (staff == null) {
                continue;
            }
            try {
                if (notifications.sendWithMetadata(staff, "IN_APP", "RESERVATION", subject, message, r.getId(), meta, key + "-" + staff.getId()) != null) {
                    sent++;
                }
            } catch (Exception e) {
                LOG.warn("No se pudo avisar al encargado {} de la reserva {}: {}", staff.getEmail(), r.getCode(), e.getMessage());
            }
        }
        return sent;
    }

    private static String vehicle(Reservation r) {
        return r.getVehicle() != null ? r.getVehicle().getModel() : "el vehículo";
    }
}
