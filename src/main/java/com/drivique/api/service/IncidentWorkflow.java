package com.drivique.api.service;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reglas de negocio de soporte: prioridad calculada, SLA y transiciones de estado permitidas. */
public final class IncidentWorkflow {

    public static final List<String> CATEGORIES = List.of("RESERVATION", "VEHICLE", "PAYMENT", "OTHER");
    private static final List<String> LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private static final Map<String, List<String>> STAFF_TRANSITIONS = Map.of(
            "OPEN", List.of("IN_REVIEW", "CLOSED"),
            "IN_REVIEW", List.of("RESOLVED", "CLOSED"),
            "RESOLVED", List.of("CLOSED", "IN_REVIEW"),
            "CLOSED", List.of());

    private static final Map<String, List<String>> OWNER_TRANSITIONS = Map.of(
            "OPEN", List.of("CLOSED"),
            "IN_REVIEW", List.of(),
            "RESOLVED", List.of("CLOSED", "IN_REVIEW"),
            "CLOSED", List.of());

    private static final List<String> CRITICAL_WORDS = List.of(
            "accidente", "choque", "colision", "robo", "hurto", "incendio", "emergencia", "herido", "volco", "atraco");
    private static final List<String> HIGH_WORDS = List.of(
            "no enciende", "no arranca", "varado", "averia", "falla", "freno", "llanta", "doble cobro", "cobro duplicado",
            "cobraron", "fraude", "no puedo pagar", "pago rechazado", "no funciona");

    private IncidentWorkflow() {
    }

    public record Classification(String priority, Duration sla) {
    }

    /** La prioridad no la elige el cliente: se calcula por categoría, palabras clave y si el vehículo está en uso. */
    public static Classification classify(String category, String text, String reservationStatusCode) {
        int level = switch (category == null ? "OTHER" : category) {
            case "VEHICLE", "PAYMENT" -> 1;
            case "RESERVATION" -> 1;
            default -> 0;
        };
        String normalized = normalize(text);
        if (CRITICAL_WORDS.stream().anyMatch(normalized::contains)) {
            level = 3;
        } else if (HIGH_WORDS.stream().anyMatch(normalized::contains)) {
            level = Math.max(level, 2);
        }
        if (level < 3 && ("IN_PROGRESS".equals(reservationStatusCode) || "ACTIVE".equals(reservationStatusCode))) {
            level += 1;
        }
        String priority = LEVELS.get(Math.min(level, 3));
        Duration sla = switch (priority) {
            case "CRITICAL" -> Duration.ofHours(4);
            case "HIGH" -> Duration.ofHours(12);
            case "MEDIUM" -> Duration.ofHours(24);
            default -> Duration.ofHours(72);
        };
        return new Classification(priority, sla);
    }

    public static List<String> allowedTransitions(String current, boolean staff) {
        return (staff ? STAFF_TRANSITIONS : OWNER_TRANSITIONS).getOrDefault(current, List.of());
    }

    public static boolean canTransition(String from, String to, boolean staff) {
        return allowedTransitions(from, staff).contains(to);
    }

    private static String normalize(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
