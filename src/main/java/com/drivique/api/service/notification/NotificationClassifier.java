package com.drivique.api.service.notification;

import com.drivique.api.model.Notification;
import com.drivique.api.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clasificación, deduplicación y serialización auxiliar para notificaciones de dominio.
 */
public final class NotificationClassifier {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationClassifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NotificationClassifier() {
    }

    public static List<Notification> deduplicate(List<Notification> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Notification> uniqueMap = new LinkedHashMap<>();
        for (Notification n : list) {
            String key = buildDeduplicationKey(n);
            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, n);
            } else {
                Notification existing = uniqueMap.get(key);
                if (!"IN_APP".equalsIgnoreCase(existing.getChannel()) && "IN_APP".equalsIgnoreCase(n.getChannel())) {
                    uniqueMap.put(key, n);
                }
            }
        }
        return new ArrayList<>(uniqueMap.values());
    }

    public static String buildDeduplicationKey(Notification n) {
        if (n == null) {
            return UUID.randomUUID().toString();
        }

        if (n.getIdempotencyKey() != null && !n.getIdempotencyKey().isBlank()) {
            String baseKey = n.getIdempotencyKey().replaceAll("-(IN_APP|EMAIL|PUSH|SMS)$", "");
            return "IDEMP:" + baseKey;
        }

        String ref = n.getReferenceId() != null ? n.getReferenceId().toString() : "noref";
        String evType = n.getEventType() != null ? n.getEventType() : (n.getType() != null ? n.getType() : "GENERAL");
        String subj = n.getSubject() != null ? n.getSubject().trim().toLowerCase(Locale.ROOT) : "";
        String msg = n.getMessage() != null ? n.getMessage().trim().toLowerCase(Locale.ROOT) : "";

        return "CONTENT:" + ref + "|" + evType + "|" + subj + "|" + msg;
    }

    public static String resolveRecipient(User user, String channel) {
        return switch (channel.toUpperCase(Locale.ROOT)) {
            case "EMAIL" -> user.getEmail();
            case "SMS" -> user.getPhone();
            case "PUSH" -> user.getEmail();
            default -> user.getEmail();
        };
    }

    public static String determineNotificationType(String eventType) {
        if (eventType == null) {
            return "GENERAL";
        }
        String upper = eventType.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("RESERVATION") || upper.contains("CANCELLATION") || upper.contains("DELIVERY")
                || upper.contains("RETURN") || upper.contains("CONTRACT")) {
            return "RESERVATION";
        }
        if (upper.startsWith("SECURITY") || upper.contains("AUTH") || upper.contains("PASSWORD")) {
            return "SECURITY";
        }
        if (upper.startsWith("PROMOTION") || upper.contains("COUPON")) {
            return "PROMOTION";
        }
        return switch (upper) {
            case "SECURITY" -> "SECURITY";
            case "RESERVATION" -> "RESERVATION";
            case "PROMOTION" -> "PROMOTION";
            default -> "GENERAL";
        };
    }

    public static String determineEventType(String eventType) {
        if (eventType == null) {
            return "GENERAL";
        }
        String upper = eventType.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("RESERVATION")) return "RESERVATION";
        if (upper.startsWith("REGISTRATION")) return "REGISTRATION";
        if (upper.startsWith("PAYMENT")) return "PAYMENT";
        if (upper.startsWith("CANCELLATION")) return "CANCELLATION";
        if (upper.startsWith("CONTRACT")) return "CONTRACT";
        if (upper.startsWith("DELIVERY")) return "DELIVERY";
        if (upper.startsWith("RETURN")) return "RETURN";
        if (upper.startsWith("DOCUMENT")) return "DOCUMENT";
        if (upper.startsWith("INCIDENT")) return "INCIDENT";
        if (upper.startsWith("SECURITY") || upper.startsWith("AUTH") || upper.startsWith("PASSWORD")) return "SECURITY";
        if (upper.startsWith("PROMOTION") || upper.startsWith("COUPON")) return "PROMOTION";
        return switch (upper) {
            case "REGISTRATION", "PAYMENT", "RESERVATION", "CANCELLATION", "CONTRACT", "DELIVERY",
                 "RETURN", "DOCUMENT", "INCIDENT", "SECURITY", "PROMOTION", "GENERAL" -> upper;
            default -> "GENERAL";
        };
    }

    public static String serializeMetadata(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            LOG.warn("No se pudo serializar metadata de notificación: {}", e.getMessage());
            return null;
        }
    }
}
