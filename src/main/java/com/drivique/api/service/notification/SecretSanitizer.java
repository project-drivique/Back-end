package com.drivique.api.service.notification;

import java.util.regex.Pattern;

public final class SecretSanitizer {

    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(bearer\\s+)[a-zA-Z0-9_\\-\\.]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_KEY_PATTERN = Pattern.compile("(?i)(key|secret|password|token|apikey|api_key|auth)[\"':=\\s]+([a-zA-Z0-9_\\-\\.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDENTIAL_URL_PATTERN = Pattern.compile("://[^:]+:([^@]+)@", Pattern.CASE_INSENSITIVE);

    private SecretSanitizer() {
    }

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String sanitized = BEARER_PATTERN.matcher(input).replaceAll("$1[REDACTED]");
        sanitized = SECRET_KEY_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = CREDENTIAL_URL_PATTERN.matcher(sanitized).replaceAll("://[REDACTED]:[REDACTED]@");
        return sanitized;
    }
}
