package com.drivique.api.service;

import com.drivique.api.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ReservationNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationNotificationService.class);

    private final NotificationService notificationService;

    public ReservationNotificationService(@Autowired(required = false) NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void sendExpirationNotification(Reservation reservation) {
        if (reservation == null || reservation.getCustomer() == null) {
            return;
        }

        String email = reservation.getCustomer().getEmail();
        String code = reservation.getCode();
        String vehicleModel = reservation.getVehicle() != null ? reservation.getVehicle().getModel() : "Vehículo";

        LOG.info("NOTIFICATION: Reservation expiration notice sent to customer '{}' for reservation '{}' (Vehicle: {}). Payment window has expired.",
                email, code, vehicleModel);

        if (notificationService != null) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("bookingCode", code);
                params.put("vehicleModel", vehicleModel);
                params.put("reason", "Expiración de plazo de pago (timeout)");
                params.put("actionPath", "/catalogo");
                params.put("actionLabel", "Explorar Catálogo de Vehículos");

                notificationService.emitDomainEvent(
                        reservation.getCustomer(),
                        "CANCELLATION",
                        "HIGH",
                        reservation.getId(),
                        params,
                        "CANCEL-EXP-" + reservation.getId()
                );
            } catch (Exception e) {
                LOG.error("Fallo al emitir notificación de cancelación por expiración para '{}': {}", code, e.getMessage());
            }
        }
    }

    public void sendPaymentWarningNotification(Reservation reservation, long hoursRemaining) {
        if (reservation == null || reservation.getCustomer() == null) {
            return;
        }

        String code = reservation.getCode();
        String vehicleModel = reservation.getVehicle() != null ? reservation.getVehicle().getModel() : "Vehículo";

        if (notificationService != null) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("bookingCode", code);
                params.put("vehicleModel", vehicleModel);
                params.put("hoursRemaining", String.valueOf(hoursRemaining));
                params.put("actionPath", "/reservas");
                params.put("actionLabel", "Pagar Reserva Ahora");

                notificationService.emitDomainEvent(
                        reservation.getCustomer(),
                        "PAYMENT_EXPIRATION_WARNING",
                        "HIGH",
                        reservation.getId(),
                        params,
                        "PAY-WARN-" + reservation.getId() + "-" + hoursRemaining
                );
            } catch (Exception e) {
                LOG.error("Fallo al emitir notificación de advertencia de pago para '{}': {}", code, e.getMessage());
            }
        }
    }
}
