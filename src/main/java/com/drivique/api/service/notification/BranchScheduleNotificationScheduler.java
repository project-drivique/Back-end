package com.drivique.api.service.notification;

import com.drivique.api.model.*;
import com.drivique.api.repository.BranchRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import com.drivique.api.service.NotificationService;
import com.drivique.api.service.ReservationDocumentReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class BranchScheduleNotificationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(BranchScheduleNotificationScheduler.class);

    private final BranchRepository branchRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository reservationStatusRepository;
    private final ReservationDocumentReviewService reservationDocumentReviewService;
    private final NotificationService notificationService;
    private final String frontendUrl;

    public BranchScheduleNotificationScheduler(
            BranchRepository branchRepository,
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            ReservationDocumentReviewService reservationDocumentReviewService,
            NotificationService notificationService,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.branchRepository = branchRepository;
        this.reservationRepository = reservationRepository;
        this.reservationStatusRepository = reservationStatusRepository;
        this.reservationDocumentReviewService = reservationDocumentReviewService;
        this.notificationService = notificationService;
        this.frontendUrl = frontendUrl;
    }

    @Scheduled(cron = "${app.jobs.branch-schedule.cron:0 */15 * * * *}")
    @Transactional
    public int processOpenBranchesReservations() {
        LocalTime now = LocalTime.now(ZoneId.of("America/Bogota"));
        List<Branch> activeBranches = branchRepository.findByActiveTrueOrderByNameAsc();
        int processedCount = 0;

        for (Branch branch : activeBranches) {
            if (isBranchCurrentlyOpen(branch, now)) {
                processedCount += processBranchReservations(branch);
            }
        }

        if (processedCount > 0) {
            LOG.info("Procesadas {} reservas que esperaban apertura de sucursal", processedCount);
        }
        return processedCount;
    }

    @Transactional
    public int processBranchReservations(Branch branch) {
        if (branch == null) {
            return 0;
        }

        List<Reservation> allReservations = reservationRepository.findAll();
        ReservationStatus pendingPayment = reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")
                .orElseGet(() -> reservationStatusRepository.save(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true)));
        ReservationStatus pendingKyc = reservationStatusRepository.findByCodeIgnoreCase("PENDING_DOCUMENT_REVIEW")
                .orElseGet(() -> reservationStatusRepository.save(new ReservationStatus("PENDING_DOCUMENT_REVIEW", "Pending review", true)));

        int count = 0;
        for (Reservation r : allReservations) {
            if (r.getStatus() != null && "PENDING_SCHEDULE".equalsIgnoreCase(r.getStatus().getCode())) {
                Branch rBranch = resolveReservationBranch(r);
                if (rBranch != null && rBranch.getId().equals(branch.getId())) {
                    boolean approved = reservationDocumentReviewService.hasApprovedRequiredDocuments(r.getCustomer().getId());
                    ReservationStatus newStatus = approved ? pendingPayment : pendingKyc;
                    r.setStatus(newStatus);

                    if (approved && (r.getCashPaymentBranch() != null || r.getCashPaymentCode() != null)) {
                        Instant standard72h = Instant.now().plus(72, ChronoUnit.HOURS);
                        Instant expiresAt = (r.getPickupDate() != null && standard72h.isAfter(r.getPickupDate()))
                                ? r.getPickupDate()
                                : standard72h;
                        r.setCashPaymentExpiresAt(expiresAt);
                    }

                    Reservation saved = reservationRepository.save(r);
                    count++;

                    sendScheduleNotifications(saved, branch, approved);
                }
            }
        }
        return count;
    }

    private Branch resolveReservationBranch(Reservation r) {
        if (r.getVehicle() != null && r.getVehicle().getCurrentBranch() != null) {
            return r.getVehicle().getCurrentBranch();
        }
        return r.getCashPaymentBranch();
    }

    public boolean isBranchCurrentlyOpen(Branch b, LocalTime time) {
        if (b == null || b.getOpeningTime() == null || b.getClosingTime() == null) {
            return true;
        }
        LocalTime open = b.getOpeningTime();
        LocalTime close = b.getClosingTime();
        boolean is24Hours = (open.equals(LocalTime.MIN) && (close.equals(LocalTime.MAX) || (close.getHour() == 23 && close.getMinute() >= 59)))
                || open.equals(close);
        if (is24Hours) {
            return true;
        }
        if (open.isBefore(close)) {
            return !time.isBefore(open) && !time.isAfter(close);
        } else {
            return !time.isBefore(open) || !time.isAfter(close);
        }
    }

    private void sendScheduleNotifications(Reservation r, Branch branch, boolean approvedKyc) {
        String path = "/reservas?detalle=" + r.getId();
        String base = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
        String fullUrl = base + path;

        Map<String, Object> metadata = Map.of(
                "actionLabel", "Continuar con mi reserva",
                "actionPath", path,
                "actionUrl", fullUrl,
                "bookingCode", r.getCode(),
                "reservationId", r.getId().toString(),
                "branchName", branch.getName()
        );

        String subject = "¡Ya puedes seguir con tu reserva en Drivique!";
        String message = approvedKyc
                ? "La sucursal " + branch.getName() + " ya abrió sus puertas. Tus documentos están al día, ya puedes continuar con tu reserva " + r.getCode() + "."
                : "La sucursal " + branch.getName() + " ya abrió sus puertas. El encargado de la sucursal estará validando tus documentos para tu reserva " + r.getCode() + ".";

        try {
            notificationService.sendWithMetadata(r.getCustomer(), "IN_APP", "RESERVATION", subject, message, r.getId(), metadata);
        } catch (Exception e) {
            LOG.warn("Error enviando notificación in-app de apertura: {}", e.getMessage());
        }

        try {
            notificationService.sendWithMetadata(r.getCustomer(), "EMAIL", "RESERVATION", subject, message, r.getId(), metadata);
        } catch (Exception e) {
            LOG.warn("Error enviando correo de apertura: {}", e.getMessage());
        }

        try {
            notificationService.sendWithMetadata(r.getCustomer(), "PUSH", "RESERVATION", "🚗 Tu sucursal ya abrió", "🚗 La sucursal " + branch.getName() + " ya abrió. Continúa con tu reserva ahora.", r.getId(), metadata);
        } catch (Exception e) {
            LOG.warn("Error enviando push de apertura: {}", e.getMessage());
        }
    }
}
