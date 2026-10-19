package com.drivique.api.service;

import com.drivique.api.model.Branch;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.DeliveryDriver;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchUserRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Avisos del domicilio: el cliente recibe conductor y PIN; el conductor recibe los datos y dos botones (confirmar o rechazar). */
@Service
public class DeliveryNotificationService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.drivique.api.service.BrandEmailTheme brandTheme;

    private String brand(String html) {
        return brandTheme == null ? html : brandTheme.apply(html);
    }


    private static final Logger LOG = LoggerFactory.getLogger(DeliveryNotificationService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final DeliveryActionTokens tokens;
    private final BranchUserRepository branchUsers;
    private final NotificationService notifications;
    private final String fromAddress;
    private final String backendUrl;

    public DeliveryNotificationService(ObjectProvider<JavaMailSender> mailSender, DeliveryActionTokens tokens, BranchUserRepository branchUsers,
                                       @Autowired(required = false) NotificationService notifications,
                                       @Value("${app.auth.mail-from:no-reply@drivique.com}") String fromAddress,
                                       @Value("${app.backend-url:http://localhost:8080}") String backendUrl) {
        this.mailSender = mailSender;
        this.tokens = tokens;
        this.branchUsers = branchUsers;
        this.notifications = notifications;
        this.fromAddress = fromAddress;
        this.backendUrl = backendUrl.replaceAll("/+$", "");
    }

    /** Al asignar el conductor: aviso al cliente (con el PIN) y correo al conductor (con el mismo PIN y los botones). */
    public void driverAssigned(ReservationDeliveryPoint point, DeliveryDriver driver, String pin) {
        Reservation reservation = point.getReservation();
        String type = "RETURN".equals(point.getPointType()) ? "devolución" : "entrega";
        String where = place(point);
        if (notifications != null && reservation.getCustomer() != null) {
            String message = "Asignamos a " + driver.getFullName() + (driver.getPhone() != null ? " (tel. " + driver.getPhone() + ")" : "")
                    + " para la " + type + " de tu reserva " + reservation.getCode() + " en " + where + ". Tu PIN de confirmación es " + pin
                    + ": entrégaselo al conductor cuando llegue.";
            for (String channel : List.of("IN_APP", "EMAIL")) {
                try {
                    notifications.sendWithMetadata(reservation.getCustomer(), channel, "RESERVATION", "Tu conductor ya fue asignado", message,
                            reservation.getId(), Map.of("actionPath", "/reservas", "actionLabel", "Ver mi reserva", "bookingCode", reservation.getCode()),
                            "DRV-ASSIGNED-" + point.getId() + "-" + pin + "-" + channel);
                } catch (Exception e) {
                    LOG.warn("No se pudo avisar al cliente del conductor asignado ({}): {}", channel, e.getMessage());
                }
            }
        }
        sendDriverEmail(point, driver, pin, type, where);
    }

    /** Cuando el cliente paga: los puntos a domicilio sin conductor se avisan a la sucursal para que asigne uno. */
    public void homeDeliveryPaid(com.drivique.api.model.Reservation reservation) {
        if (reservation == null || reservation.getDeliveryPoints() == null) {
            return;
        }
        for (ReservationDeliveryPoint point : reservation.getDeliveryPoints()) {
            if (!"HOME_DELIVERY".equals(point.getModality()) || point.getDriver() != null) {
                continue;
            }
            String kind = "RETURN".equals(point.getPointType()) ? "devolución" : "entrega";
            notifyStaff(point, "Domicilio pagado: asigna un conductor",
                    "La reserva " + reservation.getCode() + " ya fue pagada. Asigna un conductor para la " + kind
                            + " a domicilio" + (point.getAddress() != null ? " en " + point.getAddress() : "") + ".");
        }
    }

    /** Aviso a la sucursal (encargados) por un hecho del domicilio. */
    public void notifyStaff(ReservationDeliveryPoint point, String subject, String message) {
        if (notifications == null) {
            return;
        }
        Branch branch = point.getBranch() != null ? point.getBranch()
                : (point.getReservation().getVehicle() != null ? point.getReservation().getVehicle().getCurrentBranch() : null);
        if (branch == null) {
            return;
        }
        for (BranchUser assignment : branchUsers.findByBranchIdOrderByAssignedAtAsc(branch.getId())) {
            User staff = assignment.getUser();
            if (staff == null) {
                continue;
            }
            try {
                notifications.sendWithMetadata(staff, "IN_APP", "RESERVATION", subject, message, point.getReservation().getId(),
                        Map.of("actionPath", "/encargado/deliveries", "actionLabel", "Ver domicilios"),
                        "DRV-STAFF-" + point.getId() + "-" + System.nanoTime() + "-" + staff.getId());
            } catch (Exception e) {
                LOG.warn("No se pudo avisar al encargado {}: {}", staff.getEmail(), e.getMessage());
            }
        }
    }

    public void notifyCustomer(ReservationDeliveryPoint point, String subject, String message) {
        Reservation reservation = point.getReservation();
        if (notifications == null || reservation.getCustomer() == null) {
            return;
        }
        for (String channel : List.of("IN_APP", "EMAIL")) {
            try {
                notifications.sendWithMetadata(reservation.getCustomer(), channel, "RESERVATION", subject, message, reservation.getId(),
                        Map.of("actionPath", "/reservas", "actionLabel", "Ver mi reserva"),
                        "DRV-CUSTOMER-" + point.getId() + "-" + subject.hashCode() + "-" + channel);
            } catch (Exception e) {
                LOG.warn("No se pudo avisar al cliente ({}): {}", channel, e.getMessage());
            }
        }
    }

    private void sendDriverEmail(ReservationDeliveryPoint point, DeliveryDriver driver, String pin, String type, String where) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || driver.getEmail() == null || driver.getEmail().isBlank()) {
            LOG.warn("No se envió el correo al conductor {}: falta el servidor de correo o su correo", driver.getFullName());
            return;
        }
        try {
            Reservation reservation = point.getReservation();
            Instant expires = Instant.now().plus(Duration.ofDays(3));
            String confirm = link(reservation, point, "CONFIRM", expires);
            String reject = link(reservation, point, "REJECT", expires);
            User customer = reservation.getCustomer();
            String vehicle = reservation.getVehicle() != null ? reservation.getVehicle().getModel() + " (" + reservation.getVehicle().getPlate() + ")" : "";
            String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family:Arial,sans-serif;background:#f8fafc;padding:24px\">"
                    + "<div style=\"max-width:540px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:28px\">"
                    + "<h2 style=\"margin:0 0 12px;color:#0f172a\">Nueva " + type + " a domicilio</h2>"
                    + "<p style=\"color:#334155\">Hola " + esc(driver.getFullName()) + ", se te asignó la " + type + " de la reserva <b>" + esc(reservation.getCode())
                    + "</b>.</p><table style=\"width:100%;font-size:14px;color:#334155\">"
                    + row("Vehículo", vehicle) + row("Dirección", where) + row("Cliente", customer != null ? customer.getFullName() : "")
                    + row("Teléfono del cliente", customer != null && customer.getPhone() != null ? customer.getPhone() : "—")
                    + "</table><p style=\"margin:18px 0 6px;color:#334155\">Código de confirmación (el cliente debe dártelo al recibir):</p>"
                    + "<p style=\"font-size:32px;letter-spacing:8px;font-weight:800;margin:0 0 18px;color:#1e3a8a\">" + esc(pin) + "</p>"
                    + "<p style=\"color:#334155\">Cuando el cliente te dé el código, compáralo con este y elige:</p>"
                    + "<p><a href=\"" + confirm + "\" style=\"display:inline-block;background:#16a34a;color:#fff;text-decoration:none;font-weight:700;padding:12px 22px;border-radius:10px;margin-right:8px\">Código correcto: confirmar entrega</a>"
                    + "<a href=\"" + reject + "\" style=\"display:inline-block;background:#dc2626;color:#fff;text-decoration:none;font-weight:700;padding:12px 22px;border-radius:10px\">Código incorrecto: rechazar</a></p>"
                    + "<p style=\"color:#94a3b8;font-size:12px\">Los enlaces vencen en 3 días. Este es un mensaje automático de Drivique.</p></div></body></html>";
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, "Drivique"));
            helper.setTo(driver.getEmail());
            helper.setSubject("Nueva " + type + " a domicilio - reserva " + reservation.getCode());
            helper.setText("Se te asignó una " + type + " a domicilio. Código: " + pin + ". Confirmar: " + confirm + " Rechazar: " + reject, brand(html));
            sender.send(message);
            LOG.info("Correo de {} enviado al conductor {}", type, driver.getEmail());
        } catch (Exception e) {
            LOG.error("No se pudo enviar el correo al conductor {}: {}", driver.getEmail(), e.getMessage());
        }
    }

    private String link(Reservation reservation, ReservationDeliveryPoint point, String action, Instant expires) {
        return backendUrl + "/api/v1/public/deliveries/action?token=" + tokens.create(reservation.getId(), point.getPointType(), action, expires);
    }

    private static String place(ReservationDeliveryPoint point) {
        StringBuilder sb = new StringBuilder();
        if (point.getAddress() != null) {
            sb.append(point.getAddress());
        }
        if (point.getNeighborhood() != null && !point.getNeighborhood().isBlank()) {
            sb.append(", ").append(point.getNeighborhood());
        }
        if (point.getCity() != null) {
            sb.append(", ").append(point.getCity().getName());
        }
        return sb.length() == 0 ? "la dirección indicada en la reserva" : sb.toString();
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:4px 0;color:#64748b\">" + label + "</td><td style=\"padding:4px 0\"><b>" + esc(value) + "</b></td></tr>";
    }

    private static String esc(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
