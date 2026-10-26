package com.drivique.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Verificación anti-bots (Cloudflare Turnstile) hecha en el servidor: el widget del navegador solo entrega un
 * token y es este servicio quien lo valida con Cloudflare. Un token sirve una sola vez.
 *
 * En desarrollo, sin claves configuradas, se usan las claves públicas de prueba de Cloudflare (siempre aprueban).
 */
@Service
public class CaptchaService {

    private static final Logger LOG = LoggerFactory.getLogger(CaptchaService.class);

    /** Claves de prueba publicadas por Cloudflare: no protegen nada. La clave del sitio fuerza el desafío visible; la secreta siempre aprueba. */
    static final String TEST_SITE_KEY = "3x00000000000000000000FF";
    static final String TEST_SECRET_KEY = "1x0000000000000000000000000000000AA";

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final boolean enabled;
    private final String siteKey;
    private final String secretKey;
    private final String verifyUrl;
    private final RestClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public CaptchaService(
            @Value("${app.captcha.enabled:true}") boolean enabled,
            @Value("${app.captcha.site-key:}") String siteKey,
            @Value("${app.captcha.secret-key:}") String secretKey,
            @Value("${app.captcha.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}") String verifyUrl,
            @Value("${app.security.require-secrets:false}") boolean requireSecrets
    ) {
        this(enabled, siteKey, secretKey, verifyUrl, requireSecrets, RestClient.builder()
                .requestFactory(timeouts()).build());
    }

    CaptchaService(boolean enabled, String siteKey, String secretKey, String verifyUrl, boolean requireSecrets, RestClient http) {
        this.enabled = enabled;
        this.verifyUrl = verifyUrl;
        this.http = http;
        boolean configured = siteKey != null && !siteKey.isBlank() && secretKey != null && !secretKey.isBlank();
        if (enabled && !configured) {
            if (requireSecrets) {
                throw new IllegalStateException("[SEGURIDAD] El captcha está activo pero faltan TURNSTILE_SITE_KEY y TURNSTILE_SECRET_KEY. "
                        + "Créalas gratis en https://dash.cloudflare.com/?to=/:account/turnstile");
            }
            LOG.warn("[SEGURIDAD] Captcha con claves de PRUEBA de Cloudflare (siempre aprueba). Solo para desarrollo.");
            this.siteKey = TEST_SITE_KEY;
            this.secretKey = TEST_SECRET_KEY;
        } else {
            this.siteKey = siteKey == null ? "" : siteKey.trim();
            this.secretKey = secretKey == null ? "" : secretKey.trim();
        }
    }

    /** Sin tiempo máximo una lentitud de Cloudflare dejaría la petición colgada. */
    private static org.springframework.http.client.SimpleClientHttpRequestFactory timeouts() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(4));
        factory.setReadTimeout(java.time.Duration.ofSeconds(6));
        return factory;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSiteKey() {
        return siteKey;
    }

    /** Falla con IllegalArgumentException (HTTP 400) si el token falta, ya se usó, venció o es falso. */
    public void verify(String token, String clientIp) {
        if (!enabled) {
            return;
        }
        if (token == null || token.isBlank() || token.length() > 2048) {
            throw new IllegalArgumentException("Completa la verificación de seguridad (captcha) para continuar.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token.trim());
        if (clientIp != null && !clientIp.isBlank()) {
            form.add("remoteip", clientIp);
        }
        JsonNode result = null;
        RuntimeException failure = null;
        // Un reintento ante un fallo de red: así una lentitud puntual del proveedor no rechaza a una persona real
        for (int attempt = 1; attempt <= 2 && result == null; attempt++) {
            try {
                String body = http.post().uri(verifyUrl)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(String.class);
                result = body == null ? null : MAPPER.readTree(body);
            } catch (Exception ex) {
                failure = new IllegalStateException(ex.getMessage(), ex);
            }
        }
        if (result == null) {
            // Si el proveedor no responde, la acción se rechaza (nunca se deja pasar sin verificar)
            LOG.warn("No fue posible validar el captcha con el proveedor: {}", failure != null ? failure.getMessage() : "sin respuesta");
            throw new IllegalArgumentException("No fue posible validar la verificación de seguridad. Inténtalo de nuevo.");
        }
        if (result == null || !result.path("success").asBoolean(false)) {
            LOG.info("Captcha rechazado: {}", result != null ? result.path("error-codes") : "sin respuesta");
            throw new IllegalArgumentException("La verificación de seguridad no fue superada o expiró. Vuelve a intentarlo.");
        }
    }
}
