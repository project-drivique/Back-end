package com.drivique.api.service;

import com.drivique.api.dto.ExpiredReservationsSummaryDTO;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationStatus;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservationExpirationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationExpirationService.class);
    public static final String PENDING_PAYMENT_STATUS = "PENDING_PAYMENT";
    public static final String CANCELLED_BY_TIMEOUT_STATUS = "CANCELLED_BY_TIMEOUT";

    /** Plazo para pagar: 8 h, o el tiempo que falta para el retiro si es menor, con un mínimo de 2 h. */
    public static final long PAYMENT_WINDOW_HOURS = 8;

    public static Instant paymentDeadline(Instant now, Instant pickupDate) {
        Instant standard = now.plus(PAYMENT_WINDOW_HOURS, java.time.temporal.ChronoUnit.HOURS);
        Instant deadline = (pickupDate != null && pickupDate.isBefore(standard)) ? pickupDate : standard;
        Instant minimum = now.plus(2, java.time.temporal.ChronoUnit.HOURS);
        return deadline.isBefore(minimum) ? minimum : deadline;
    }

    /**
     * Reserva que espera a la sucursal (apertura u revisión de documentos): el reloj del pago aún no corre.
     * Retiene las fechas hasta la hora de retiro (mínimo 2 h) para no bloquearlas para siempre si nadie la gestiona.
     */
    public static Instant waitingDeadline(Instant now, Instant pickupDate) {
        Instant minimum = now.plus(2, java.time.temporal.ChronoUnit.HOURS);
        return (pickupDate == null || pickupDate.isBefore(minimum)) ? minimum : pickupDate;
    }

    /** Estados pendientes que retienen fechas y vencen si nadie los gestiona dentro del plazo. */
    private static final List<String> EXPIRABLE_STATUSES = List.of("PENDING_PAYMENT", "PENDING_SCHEDULE", "PENDING_DOCUMENT_REVIEW");

    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository reservationStatusRepository;
    private final ReservationNotificationService notificationService;

    public ReservationExpirationService(
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            ReservationNotificationService notificationService
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationStatusRepository = reservationStatusRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ExpiredReservationsSummaryDTO expirePendingReservations() {
        Instant now = Instant.now();
        List<Reservation> expiredReservations = new ArrayList<>();
        for (String statusCode : EXPIRABLE_STATUSES) {
            List<Reservation> found = reservationRepository.findExpiredPendingReservations(statusCode, now);
            if (found != null) {
                expiredReservations.addAll(found);
            }
        }

        // Aviso a reservas pendientes con vencimiento próximo (próximas 24 horas)
        try {
            Instant warningWindow = now.plus(24, java.time.temporal.ChronoUnit.HOURS);
            List<Reservation> expiringSoon = new ArrayList<>();
            for (String statusCode : EXPIRABLE_STATUSES) {
                List<Reservation> found = reservationRepository.findExpiringPendingReservations(statusCode, now, warningWindow);
                if (found != null) {
                    expiringSoon.addAll(found);
                }
            }
            for (Reservation reservation : expiringSoon) {
                if (reservation.getCashPaymentExpiresAt() != null) {
                    long hoursRemaining = Math.max(1, java.time.Duration.between(now, reservation.getCashPaymentExpiresAt()).toHours());
                    notificationService.sendPaymentWarningNotification(reservation, hoursRemaining);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Error al procesar advertencias de plazo de pago: {}", ex.getMessage());
        }

        if (expiredReservations.isEmpty()) {
            LOG.debug("No expired pending reservations found at {}", now);
            return new ExpiredReservationsSummaryDTO(0, List.of(), now);
        }

        ReservationStatus cancelledStatus = reservationStatusRepository.findByCodeIgnoreCase(CANCELLED_BY_TIMEOUT_STATUS)
                .orElseGet(() -> reservationStatusRepository.save(
                        new ReservationStatus(CANCELLED_BY_TIMEOUT_STATUS, "Cancelada por expiración de plazo de pago", false)
                ));

        List<String> expiredCodes = new ArrayList<>();

        for (Reservation reservation : expiredReservations) {
            reservation.setStatus(cancelledStatus);
            reservation.setBlocksAvailability(false);
            reservationRepository.save(reservation);

            expiredCodes.add(reservation.getCode());

            // Emit client notification (Email + In-App multi-channel)
            notificationService.sendExpirationNotification(reservation);

            // Audit log
            LOG.info("AUDIT: Reservation '{}' (customer: '{}', vehicleId: '{}', expiry: '{}') has been CANCELLED_BY_TIMEOUT. Vehicle availability released.",
                    reservation.getCode(),
                    reservation.getCustomer() != null ? reservation.getCustomer().getEmail() : "N/A",
                    reservation.getVehicle() != null ? reservation.getVehicle().getId() : "N/A",
                    reservation.getCashPaymentExpiresAt());
        }

        LOG.info("AUDIT: Total of {} pending reservations expired and cancelled by timeout at {}", expiredCodes.size(), now);
        return new ExpiredReservationsSummaryDTO(expiredCodes.size(), expiredCodes, now);
    }
}
