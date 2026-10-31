package com.drivique.api.service;

import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.repository.ReservationDeliveryPointRepository;
import com.drivique.api.service.email.EmailLanguage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Caso de uso para previsualizar y ejecutar acciones de entrega o devolución por parte del conductor.
 */
@Service
public class DeliveryDriverActionService {

    private final DeliveryActionTokens tokens;
    private final DeliveryLogisticsService logistics;
    private final ReservationDeliveryPointRepository deliveryPoints;
    private final DeliveryDriverPortalViewRenderer viewRenderer;
    private final EmailLanguage emailLanguage;

    public DeliveryDriverActionService(
            DeliveryActionTokens tokens,
            DeliveryLogisticsService logistics,
            ReservationDeliveryPointRepository deliveryPoints,
            DeliveryDriverPortalViewRenderer viewRenderer,
            @Autowired(required = false) EmailLanguage emailLanguage) {
        this.tokens = tokens;
        this.logistics = logistics;
        this.deliveryPoints = deliveryPoints;
        this.viewRenderer = viewRenderer != null ? viewRenderer : new DeliveryDriverPortalViewRenderer();
        this.emailLanguage = emailLanguage;
    }

    public ResponseEntity<String> preview(String token, String actionUrl) {
        Locale locale = resolveLocale();
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return viewRenderer.errorPage(
                    locale,
                    410,
                    viewRenderer.t(locale, "page.expiredTitle"),
                    viewRenderer.t(locale, "page.expiredPreview"),
                    "expired");
        }

        DeliveryActionTokens.Action a = action.get();
        ReservationDeliveryPoint point = findPoint(a);

        if (point != null && "COMPLETED".equals(point.getDeliveryStatus())) {
            return viewRenderer.completedNoticePage(locale, point);
        }

        if (isBeforeEventDate(point, a.pointType())) {
            return viewRenderer.lockedFutureDeliveryPage(locale, point, a.pointType());
        }

        boolean isConfirm = "CONFIRM".equalsIgnoreCase(a.action());
        return viewRenderer.renderConfirmationCard(locale, isConfirm, token, actionUrl, point, a.pointType());
    }

    public ResponseEntity<String> execute(String token) {
        Locale locale = resolveLocale();
        Optional<DeliveryActionTokens.Action> action = tokens.parse(token);
        if (action.isEmpty()) {
            return viewRenderer.errorPage(
                    locale,
                    410,
                    viewRenderer.t(locale, "page.expiredTitle"),
                    viewRenderer.t(locale, "page.expiredPost"),
                    "expired");
        }

        DeliveryActionTokens.Action a = action.get();
        ReservationDeliveryPoint point = findPoint(a);

        if (point != null && "COMPLETED".equals(point.getDeliveryStatus())) {
            return viewRenderer.completedNoticePage(locale, point);
        }

        if (isBeforeEventDate(point, a.pointType())) {
            return viewRenderer.errorPage(
                    locale,
                    400,
                    viewRenderer.t(locale, "page.notYetTitle"),
                    viewRenderer.t(locale, "page.notYetText"),
                    "scheduled");
        }

        try {
            if ("CONFIRM".equalsIgnoreCase(a.action())) {
                logistics.confirmByDriver(a.reservationId(), a.pointType());
                return viewRenderer.successResultPage(locale, point, a.pointType(), true);
            } else {
                logistics.rejectByDriver(a.reservationId(), a.pointType());
                return viewRenderer.successResultPage(locale, point, a.pointType(), false);
            }
        } catch (RuntimeException e) {
            String message = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : viewRenderer.t(locale, "page.conflictDefault");
            return viewRenderer.errorPage(
                    locale,
                    409,
                    viewRenderer.t(locale, "page.conflictTitle"),
                    message,
                    "conflict");
        }
    }

    private Locale resolveLocale() {
        return emailLanguage != null ? emailLanguage.fromRequest() : EmailLanguage.DEFAULT;
    }

    private ReservationDeliveryPoint findPoint(DeliveryActionTokens.Action a) {
        if (deliveryPoints == null || a.reservationId() == null || a.pointType() == null) {
            return null;
        }
        try {
            return deliveryPoints.findDetailedByReservationIdAndPointType(a.reservationId(), a.pointType().toUpperCase())
                    .orElseGet(() -> deliveryPoints.findByReservationIdAndPointType(a.reservationId(), a.pointType().toUpperCase()).orElse(null));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isBeforeEventDate(ReservationDeliveryPoint point, String pointType) {
        if (point == null) {
            return false;
        }
        Reservation res = point.getReservation();
        if (res == null) {
            return false;
        }
        Instant target = "RETURN".equalsIgnoreCase(pointType) ? res.getReturnDate() : res.getPickupDate();
        if (target == null) {
            return false;
        }
        ZoneId zone = ZoneId.of("America/Bogota");
        LocalDate eventDate = target.atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(zone);
        return today.isBefore(eventDate);
    }
}
