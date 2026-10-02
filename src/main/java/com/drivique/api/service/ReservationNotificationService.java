package com.drivique.api.service;

import com.drivique.api.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReservationNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationNotificationService.class);

    public void sendExpirationNotification(Reservation reservation) {
        String email = reservation.getCustomer() != null ? reservation.getCustomer().getEmail() : "unknown";
        String code = reservation.getCode();
        String vehicleModel = reservation.getVehicle() != null ? reservation.getVehicle().getModel() : "unknown";

        LOG.info("NOTIFICATION: Reservation expiration notice sent to customer '{}' for reservation '{}' (Vehicle: {}). Payment window has expired.",
                email, code, vehicleModel);
    }
}
