package com.drivique.api.service;

import com.drivique.api.model.Promotion;
import com.drivique.api.model.User;
import com.drivique.api.repository.PromotionRepository;
import com.drivique.api.repository.ReservationRepository;
import com.drivique.api.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Avisa a los clientes de las promociones y cupones activos y vigentes; cada promoción se anuncia una sola vez. */
@Service
public class PromotionNotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PromotionNotificationService.class);

    private final PromotionRepository promotions;
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final NotificationService notifications;

    public PromotionNotificationService(PromotionRepository promotions, UserRepository users, ReservationRepository reservations,
                                        @Autowired(required = false) NotificationService notifications) {
        this.promotions = promotions;
        this.users = users;
        this.reservations = reservations;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.jobs.promotion-notifications.cron:0 */30 * * * *}")
    public void scheduled() {
        try {
            int sent = notifyPending();
            if (sent > 0) {
                LOG.info("Avisos de promociones enviados: {}", sent);
            }
        } catch (Exception e) {
            LOG.error("Error enviando avisos de promociones", e);
        }
    }

    /** Devuelve cuántos avisos se generaron. */
    @Transactional
    public int notifyPending() {
        if (notifications == null) {
            return 0;
        }
        Instant now = Instant.now();
        int sent = 0;
        for (Promotion p : promotions.findByActiveTrueAndNotificationSentAtIsNull()) {
            boolean valid = !p.getStartsAt().isAfter(now) && (p.getEndsAt() == null || p.getEndsAt().isAfter(now));
            if (!valid) {
                continue;
            }
            List<User> customers = users.findActiveByRoleCode("CUSTOMER");
            for (User customer : customers) {
                long completed = reservations.countByCustomerIdAndStatusCode(customer.getId(), "COMPLETED");
                if (!PromotionAdminService.audienceAllows(p.getAudience(), customer.getEmail(), completed)) {
                    continue;
                }
                sent += send(p, customer);
            }
            p.markNotified();
            promotions.save(p);
        }
        return sent;
    }

    private int send(Promotion p, User customer) {
        String title = p.getName() != null && !p.getName().isBlank() ? p.getName() : p.getCode();
        String discount = "PERCENTAGE".equals(p.getDiscountType())
                ? p.getDiscountValue().stripTrailingZeros().toPlainString() + "%"
                : "$" + p.getDiscountValue().setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
        String subject = "Nueva promoción: " + title;
        String message = "Hola " + customer.getFirstName() + ", tienes " + discount + " de descuento con el "
                + ("COUPON".equals(p.getOfferType()) ? "cupón " + p.getCode() : "la promoción " + title) + ". Aplícalo en tu próxima reserva.";
        Map<String, Object> meta = Map.of("actionPath", "/home", "actionLabel", "Ver vehículos", "promotionCode", p.getCode());
        int sent = 0;
        for (String channel : List.of("IN_APP", "EMAIL")) {
            try {
                if (notifications.sendWithMetadata(customer, channel, "PROMOTION", subject, message, p.getId(), meta,
                        "PROMO-" + p.getId() + "-" + customer.getId() + "-" + channel) != null) {
                    sent++;
                }
            } catch (Exception e) {
                LOG.warn("No se pudo avisar de la promoción {} a {}: {}", p.getCode(), customer.getEmail(), e.getMessage());
            }
        }
        return sent;
    }
}
