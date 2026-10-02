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
        List<Reservation> expiredReservations = reservationRepository.findExpiredPendingReservations(PENDING_PAYMENT_STATUS, now);

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

            // Emit client notification
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
