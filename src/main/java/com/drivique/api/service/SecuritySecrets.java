package com.drivique.api.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Política única para los secretos criptográficos (firma JWT, enlaces de domicilio, cifrado de documentos).
 * Ningún secreto sale del código: se entrega por variable de entorno y los valores que alguna vez
 * estuvieron escritos en el repositorio se tratan como comprometidos.
 */
public final class SecuritySecrets {

    public static final int MIN_LENGTH = 32;

    /** Valores que estuvieron versionados en el repositorio: son públicos y jamás deben usarse. */
    private static final Set<String> PUBLISHED = Set.of(
            "driviqueSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong2026!",
            "driviqueSecureMasterAuthSigningSecretKeyHMAC512Sha2026GeneratedProductionSafe!",
            "drivique-kyc-aes-256-gcm-master-key-secret-2026!",
            "your-512-bit-secure-signing-secret-key-here"
    );

    private SecuritySecrets() {
    }

    public static boolean isPublished(String value) {
        return value != null && PUBLISHED.contains(value.trim());
    }

    /** Un secreto es utilizable si no está vacío, no es público y tiene al menos 32 caracteres. */
    public static boolean isUsable(String value) {
        return value != null && value.trim().length() >= MIN_LENGTH && !isPublished(value);
    }

    /**
     * Devuelve el secreto configurado. Si falta o no es seguro:
     * en ambientes protegidos ({@code requireSecrets}) detiene el arranque; en desarrollo genera uno efímero
     * (se pierde al reiniciar) y lo avisa en el log.
     */
    public static String resolveOrEphemeral(String name, String value, boolean requireSecrets, Logger log) {
        if (isUsable(value)) {
            return value.trim();
        }
        String reason = value == null || value.isBlank() ? "no está configurado"
                : isPublished(value) ? "coincide con un valor público del repositorio"
                : "tiene menos de " + MIN_LENGTH + " caracteres";
        if (requireSecrets) {
            throw new IllegalStateException("[SEGURIDAD] " + name + " " + reason
                    + ". Defínelo como variable de entorno (mínimo " + MIN_LENGTH + " caracteres aleatorios). Ej.: openssl rand -base64 48");
        }
        log.warn("[SEGURIDAD] {} {}. Se usa un valor temporal solo para desarrollo: se pierde al reiniciar.", name, reason);
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        return Base64.getEncoder().encodeToString(random);
    }
}
