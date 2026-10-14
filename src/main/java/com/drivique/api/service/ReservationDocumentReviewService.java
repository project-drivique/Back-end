package com.drivique.api.service;

import com.drivique.api.exception.ConflictException;
import com.drivique.api.model.*;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.ReservationStatusRepository;
import com.drivique.api.repository.UserDocumentRepository;
import com.drivique.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReservationDocumentReviewService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationDocumentReviewService.class);

    private static final Set<String> IDENTITY_DOC_CODES = Set.of("CC", "CE", "PASSPORT");

    private final UserDocumentRepository userDocumentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusRepository reservationStatusRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public ReservationDocumentReviewService(
            UserDocumentRepository userDocumentRepository,
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            NotificationService notificationService,
            String frontendUrl
    ) {
        this(userDocumentRepository, reservationRepository, reservationStatusRepository, notificationService, null, frontendUrl);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ReservationDocumentReviewService(
            UserDocumentRepository userDocumentRepository,
            ReservationRepository reservationRepository,
            ReservationStatusRepository reservationStatusRepository,
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepository userRepository,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.userDocumentRepository = userDocumentRepository;
        this.reservationRepository = reservationRepository;
        this.reservationStatusRepository = reservationStatusRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    @Transactional(readOnly = true)
    public boolean hasApprovedRequiredDocuments(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (userRepository != null) {
            Optional<User> uOpt = userRepository.findById(userId);
            if (uOpt.isPresent() && uOpt.get().isProfileComplete()) {
                return true;
            }
        }

        List<UserDocument> userDocs = userDocumentRepository.findByUserId(userId);
        boolean hasApprovedLicense = false;
        boolean hasApprovedIdentity = false;

        for (UserDocument doc : userDocs) {
            if (doc.getStatus() != null && "APPROVED".equalsIgnoreCase(doc.getStatus().getCode())
                    && doc.getDocumentType() != null && doc.getDocumentType().getCode() != null) {
                String code = doc.getDocumentType().getCode().trim().toUpperCase(Locale.ROOT);
                if ("DRIVER_LICENSE".equals(code)) {
                    hasApprovedLicense = true;
                } else if (IDENTITY_DOC_CODES.contains(code)) {
                    hasApprovedIdentity = true;
                }
            }
        }

        return hasApprovedLicense && hasApprovedIdentity;
    }

    public void validateCanBePaid(Reservation reservation) {
        if (reservation != null && reservation.getStatus() != null) {
            String code = reservation.getStatus().getCode();
            if ("PENDING_DOCUMENT_REVIEW".equalsIgnoreCase(code)) {
                throw new ConflictException("La reserva se encuentra en revisión de documentos y no puede ser pagada hasta que los documentos sean aprobados por el encargado de la sucursal.");
            }
            if ("PENDING_SCHEDULE".equalsIgnoreCase(code)) {
                throw new ConflictException("La reserva fue registrada fuera del horario de atención de la sucursal y se activará automáticamente al inicio de la jornada de atención.");
            }
        }
    }

    @Transactional
    public void onDocumentsReviewed(User user) {
        if (user == null || !hasApprovedRequiredDocuments(user.getId())) {
            return;
        }

        List<Reservation> userReservations = reservationRepository.findByCustomerOrderByCreatedAtDesc(user);
        ReservationStatus pendingPaymentStatus = reservationStatusRepository.findByCodeIgnoreCase("PENDING_PAYMENT")
                .orElseGet(() -> reservationStatusRepository.save(new ReservationStatus("PENDING_PAYMENT", "Pending payment", true)));

        for (Reservation r : userReservations) {
            if (r.getStatus() != null && "PENDING_DOCUMENT_REVIEW".equalsIgnoreCase(r.getStatus().getCode())) {
                r.setStatus(pendingPaymentStatus);

                if (r.getCashPaymentBranch() != null || r.getCashPaymentCode() != null) {
                    Instant standard72h = Instant.now().plus(72, ChronoUnit.HOURS);
                    Instant expiresAt = (r.getPickupDate() != null && standard72h.isAfter(r.getPickupDate()))
                            ? r.getPickupDate()
                            : standard72h;
                    r.setCashPaymentExpiresAt(expiresAt);
                }

                Reservation saved = reservationRepository.save(r);

                String path = "/reservas?detalle=" + saved.getId();
                String base = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
                String fullUrl = base + path;
                Map<String, Object> metadata = Map.of(
                        "actionLabel", "Continuar con mi reserva",
                        "actionPath", path,
                        "actionUrl", fullUrl,
                        "bookingCode", saved.getCode(),
                        "reservationId", saved.getId().toString()
                );

                String subject = "¡Documentos aprobados! Ya puedes pagar tu reserva";
                String message = "Tus documentos fueron aprobados por la sucursal. Tu reserva " + saved.getCode()
                        + " ahora está lista para ser pagada.";

                try {
                    notificationService.sendWithMetadata(user, "IN_APP", "RESERVATION", subject, message, saved.getId(), metadata);
                } catch (Exception e) {
                    LOG.warn("Error al enviar notificación in-app de documentos aprobados: {}", e.getMessage());
                }

                try {
                    notificationService.sendWithMetadata(user, "EMAIL", "RESERVATION", subject, message, saved.getId(), metadata);
                } catch (Exception e) {
                    LOG.warn("Error al enviar correo de documentos aprobados: {}", e.getMessage());
                }
            }
        }
    }
}
