package com.drivique.api.service;

import com.drivique.api.model.Branch;
import com.drivique.api.model.BranchUser;
import com.drivique.api.model.DeliveryDriver;
import com.drivique.api.model.Reservation;
import com.drivique.api.model.ReservationDeliveryPoint;
import com.drivique.api.model.User;
import com.drivique.api.repository.BranchUserRepository;
import com.drivique.api.service.email.EmailLanguage;
import com.drivique.api.service.email.EmailTexts;
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

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailLanguage emailLanguage;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailTexts emailTexts;

    private static final EmailTexts DEFAULT_TEXTS = new EmailTexts();
    private static final String NO_ADDRESS = "la dirección indicada en la reserva";

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
    private final java.util.Map<String, Long> lastSentMap = new java.util.concurrent.ConcurrentHashMap<>();

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

        // Deduplicación para evitar correos duplicados por clicks rápidos
        String dedupKey = point.getId() + ":" + driver.getId();
        long now = System.currentTimeMillis();
        Long prev = lastSentMap.put(dedupKey, now);
        if (prev != null && (now - prev) < 15_000) {
            LOG.info("Omitiendo correo duplicado al conductor {} para entrega {} (enviado hace {} ms)", driver.getEmail(), point.getId(), now - prev);
            return;
        }

        // Envío asíncrono para que la API responda inmediatamente a la interfaz sin bloquear al usuario
        java.util.Locale driverLocale = emailLanguage != null ? emailLanguage.forEmail(driver.getEmail()) : EmailLanguage.DEFAULT;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                sendDriverEmail(point, driver, pin, type, where, driverLocale);
            } catch (Exception ex) {
                LOG.error("Error en envío asíncrono de correo a {}: {}", driver.getEmail(), ex.getMessage());
            }
        });
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

    private void sendDriverEmail(ReservationDeliveryPoint point, DeliveryDriver driver, String pin, String type, String where, java.util.Locale locale) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || driver.getEmail() == null || driver.getEmail().isBlank()) {
            LOG.warn("No se envió el correo al conductor {}: falta el servidor de correo o su correo", driver.getFullName());
            return;
        }
        try {
            Reservation reservation = point.getReservation();
            Instant eventDate = "RETURN".equals(point.getPointType()) ? reservation.getReturnDate() : reservation.getPickupDate();
            if (eventDate == null) {
                eventDate = Instant.now();
            }
            java.time.ZonedDateTime eventZdt = eventDate.atZone(java.time.ZoneId.of("America/Bogota"));
            java.time.Instant expires = eventZdt.toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.of("America/Bogota")).toInstant();
            if (expires.isBefore(Instant.now())) {
                expires = Instant.now().atZone(java.time.ZoneId.of("America/Bogota")).toLocalDate().atTime(23, 59, 59).atZone(java.time.ZoneId.of("America/Bogota")).toInstant().plus(Duration.ofHours(24));
            }
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(java.time.ZoneId.of("America/Bogota"));
            String dayStr = fmt.format(eventZdt);

            EmailTexts texts = emailTexts != null ? emailTexts : DEFAULT_TEXTS;
            String kind = "RETURN".equals(point.getPointType()) ? "RETURN" : "DELIVERY";
            String shownWhere = NO_ADDRESS.equals(where) ? texts.get(locale, "driver.noAddress") : where;
            String confirm = link(reservation, point, "CONFIRM", expires);
            String reject = link(reservation, point, "REJECT", expires);
            User customer = reservation.getCustomer();
            String vehicle = reservation.getVehicle() != null ? reservation.getVehicle().getModel() + " (" + reservation.getVehicle().getPlate() + ")" : "";
            String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(where, java.nio.charset.StandardCharsets.UTF_8);
            String html = "<!DOCTYPE html><html lang=\"" + locale.toLanguageTag() + "\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>"
                    + "<body style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;\">"
                    + "<div style=\"max-width: 520px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);\">"
                    + "  <div style=\"background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;\">"
                    + "    <h1 style=\"color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;\">DRIVIQUE</h1>"
                    + "    <p style=\"color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;\">" + texts.get(locale, "common.tagline") + "</p>"
                    + "  </div>"
                    + "  <div style=\"padding: 30px 32px 24px;\">"
                    + "    <h2 style=\"color: #0f172a; margin: 0 0 10px; font-size: 18px; font-weight: 700;\">" + texts.get(locale, "driver.title." + kind) + "</h2>"
                    + "    <p style=\"color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 18px;\">"
                    + "      " + texts.get(locale, "driver.intro." + kind, "name", esc(driver.getFullName()), "code", esc(reservation.getCode()))
                    + "    </p>"
                    + "    <div style=\"background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 14px 18px; margin: 0 0 20px;\">"
                    + "      <table style=\"width: 100%; border-collapse: collapse; font-size: 13.5px;\">"
                    + "        <tr><td style=\"padding: 5px 0; color: #64748b; width: 32%;\">" + texts.get(locale, "driver.vehicle") + "</td><td style=\"padding: 5px 0; color: #0f172a; font-weight: 600;\">" + esc(vehicle) + "</td></tr>"
                    + "        <tr><td style=\"padding: 5px 0; color: #64748b;\">" + texts.get(locale, "driver.address") + "</td><td style=\"padding: 5px 0; color: #0f172a; font-weight: 600;\">" + esc(shownWhere) + "</td></tr>"
                    + "        <tr><td style=\"padding: 5px 0; color: #64748b;\">" + texts.get(locale, "driver.customer") + "</td><td style=\"padding: 5px 0; color: #0f172a; font-weight: 600;\">" + esc(customer != null ? customer.getFullName() : "—") + (customer != null && customer.getPhone() != null ? " (" + esc(customer.getPhone()) + ")" : "") + "</td></tr>"
                    + "      </table>"
                    + "    </div>"
                    + "    <p style=\"color: #475569; font-size: 13.5px; font-weight: 600; margin: 0 0 8px; text-align: center;\">" + texts.get(locale, "driver.codeHint") + "</p>"
                    + "    <div style=\"background-color: #f1f5f9; border: 2px dashed #cbd5e1; border-radius: 12px; padding: 16px; text-align: center; margin: 0 0 16px;\">"
                    + "      <span style=\"font-family: 'Courier New', Courier, monospace; font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #1e3a8a;\">" + esc(pin) + "</span>"
                    + "    </div>"
                    + "    <div style=\"margin: 0 0 12px;\">"
                    + "      <a href=\"" + confirm + "\" style=\"display: block; box-sizing: border-box; background: #16a34a; color: #ffffff; text-decoration: none; font-weight: 700; font-size: 14px; padding: 13px 20px; border-radius: 10px; text-align: center; box-shadow: 0 3px 8px rgba(22, 163, 74, 0.28);\">" + texts.get(locale, "driver.confirm." + kind) + "</a>"
                    + "    </div>"
                    + "    <div style=\"text-align: center; margin-bottom: 20px;\">"
                    + "      <a href=\"" + reject + "\" style=\"color: #dc2626; font-size: 12.5px; text-decoration: none; font-weight: 600;\">" + texts.get(locale, "driver.reject") + "</a>"
                    + "    </div>"
                    + "    <p style=\"color: #64748b; font-size: 12px; line-height: 1.5; margin: 0 0 18px; text-align: center;\">" + texts.get(locale, "driver.validity." + kind, "day", dayStr) + "</p>"
                    + "    <div style=\"border-top: 1px solid #e2e8f0; padding-top: 16px; text-align: center;\">"
                    + "      <p style=\"color: #94a3b8; font-size: 11.5px; margin: 0;\">" + texts.get(locale, "common.opsNotice") + "</p>"
                    + "    </div>"
                    + "  </div>"
                    + "</div></body></html>";
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, texts.brandName()));
            helper.setTo(driver.getEmail());
            helper.setSubject(texts.get(locale, "driver.subject." + kind, "code", reservation.getCode()));
            helper.setText(texts.get(locale, "driver.text." + kind, "pin", pin, "confirm", confirm, "reject", reject), brand(html));
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
