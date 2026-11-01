package com.drivique.api.service;

import com.drivique.api.model.AuditLog;
import com.drivique.api.model.Branch;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.User;
import com.drivique.api.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReservationEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private ReservationEventPublisher() {
    }

    static void notifyInitialCreation(
            NotificationService notificationService,
            Reservation savedReservation,
            String initialStatusCode,
            Branch pickupBranch,
            boolean documentsApproved
    ) {
        if ("PENDING_SCHEDULE".equals(initialStatusCode)) {
            String branchName = pickupBranch != null ? pickupBranch.getName() : "la sucursal";
            String openTime = pickupBranch != null && pickupBranch.getOpeningTime() != null
                    ? pickupBranch.getOpeningTime().toString()
                    : "su apertura";
            String subject = "Reserva registrada fuera de horario";
            String message = "Recibimos tu reserva " + savedReservation.getCode() + " fuera del horario de atención de "
                    + branchName + ". Cuando inicie la jornada a las " + openTime
                    + ", te notificaremos automáticamente para continuar con tu reserva.";
            notificationService.send(savedReservation.getCustomer(), "EMAIL", "RESERVATION_PENDING_SCHEDULE",
                    subject, message, savedReservation.getId());
            notificationService.send(savedReservation.getCustomer(), "IN_APP", "RESERVATION_PENDING_SCHEDULE",
                    subject, message, savedReservation.getId());
        } else if (documentsApproved) {
            notificationService.send(savedReservation.getCustomer(), "EMAIL", "RESERVATION_PENDING_PAYMENT",
                    "Reserva recibida - pago pendiente",
                    "Recibimos tu reserva " + savedReservation.getCode()
                            + ". Quedará pendiente hasta que el pago sea validado en la sucursal o en línea. Después podrás firmar el contrato y recibir el PDF.",
                    savedReservation.getId());
        } else {
            String subject = "Reserva registrada - Documentos en validación";
            String message = "Recibimos tu reserva " + savedReservation.getCode()
                    + ". Tus documentos de identidad y licencia de conducción están en proceso de validación por parte del encargado de la sucursal. Te notificaremos una vez sean revisados y aprobados.";
            notificationService.send(savedReservation.getCustomer(), "EMAIL", "RESERVATION_PENDING_DOCUMENT_REVIEW",
                    subject, message, savedReservation.getId());
            notificationService.send(savedReservation.getCustomer(), "IN_APP", "RESERVATION_PENDING_DOCUMENT_REVIEW",
                    subject, message, savedReservation.getId());
        }
    }

    static void recordAudit(AuditLogRepository auditLogRepository, UUID reservationId, User user, String detail) {
        auditLogRepository.save(new AuditLog(
                "RESERVATIONS",
                "Reservation",
                reservationId,
                "UPDATE",
                "SUCCESS",
                user,
                null,
                null,
                detail,
                null,
                null
        ));
    }

    static void notifyPaymentConfirmed(
            NotificationService notificationService,
            Reservation saved,
            boolean hasApprovedPayment
    ) {
        try {
            notificationService.emitDomainEvent(
                    saved.getCustomer(),
                    "RESERVATION",
                    "HIGH",
                    saved.getId(),
                    Map.of(
                            "bookingCode", saved.getCode(),
                            "pickupDate", saved.getPickupDate().toString(),
                            "returnDate", saved.getReturnDate().toString()
                    ),
                    "RES-" + saved.getId()
            );
        } catch (Throwable e) {
            LOG.debug("No se pudo emitir evento RESERVATION tras confirmar pago: {}", e.getMessage());
        }

        if (hasApprovedPayment) {
            try {
                notificationService.emitDomainEvent(
                        saved.getCustomer(),
                        "PAYMENT",
                        "HIGH",
                        saved.getId(),
                        Map.of(
                                "bookingCode", saved.getCode(),
                                "amount", saved.getTotalEstimated() != null ? saved.getTotalEstimated().toString() : "0",
                                "currency", "COP",
                                "status", "APPROVED",
                                "actionPath", "/contrato/" + saved.getId(),
                                "actionLabel", "Continuar y Firmar mi Contrato"
                        ),
                        "PAY-" + saved.getId()
                );
            } catch (Throwable e) {
                LOG.debug("No se pudo emitir evento PAYMENT tras confirmar pago: {}", e.getMessage());
            }
        }
    }

    static void notifyPaymentFailed(NotificationService notificationService, User user, Reservation r) {
        try {
            notificationService.emitDomainEvent(
                    user,
                    "PAYMENT",
                    "HIGH",
                    r.getId(),
                    Map.of(
                            "bookingCode", r.getCode(),
                            "amount", r.getTotalEstimated() != null ? r.getTotalEstimated().toString() : "0",
                            "currency", "COP",
                            "status", "DECLINED",
                            "actionPath", "/reservas",
                            "actionLabel", "Reintentar Pago en Mis Reservas"
                    ),
                    "PAY-FAIL-" + UUID.randomUUID()
            );
        } catch (Throwable e) {
            LOG.debug("No se pudo emitir evento de pago fallido: {}", e.getMessage());
        }
    }

    static void notifyCancellation(NotificationService notificationService, Reservation saved, String reason) {
        try {
            notificationService.emitDomainEvent(
                    saved.getCustomer(),
                    "CANCELLATION",
                    "HIGH",
                    saved.getId(),
                    Map.of(
                            "bookingCode", saved.getCode(),
                            "reason", reason != null ? reason : "Cancelada por el usuario"
                    ),
                    "CANCEL-" + saved.getId()
            );
        } catch (Exception e) {
            LOG.debug("No se pudo emitir notificación de cancelación: {}", e.getMessage());
        }
    }
}

