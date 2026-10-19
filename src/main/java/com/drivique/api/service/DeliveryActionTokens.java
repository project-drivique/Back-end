package com.drivique.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Enlaces firmados para que el conductor confirme o rechace una entrega desde su correo, sin iniciar sesión. */
@Component
public class DeliveryActionTokens {

    public record Action(UUID reservationId, String pointType, String action) {}

    private final byte[] key;

    public DeliveryActionTokens(@Value("${jwt.secret:driviqueSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong2026!}") String secret) {
        this.key = ("delivery-action:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    public String create(UUID reservationId, String pointType, String action, Instant expiresAt) {
        String payload = reservationId + "|" + pointType + "|" + action + "|" + expiresAt.getEpochSecond();
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + sign(body);
    }

    /** Vacío si el enlace está alterado o venció. */
    public Optional<Action> parse(String token) {
        try {
            if (token == null) {
                return Optional.empty();
            }
            if (token.contains(",")) {
                token = token.split(",")[0].trim();
            }
            token = token.trim();
            if (!token.contains(".")) {
                return Optional.empty();
            }
            String[] parts = token.split("\\.", 2);
            if (!MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            String[] f = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|");
            if (f.length != 4 || Instant.now().getEpochSecond() > Long.parseLong(f[3])) {
                return Optional.empty();
            }
            return Optional.of(new Action(UUID.fromString(f[0]), f[1], f[2]));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar el enlace", e);
        }
    }
}
