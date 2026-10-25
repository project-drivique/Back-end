package com.drivique.api.service;

import com.drivique.api.dto.OAuthUserInfo;
import com.drivique.api.dto.SocialLoginRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Service
public class OAuthProviderService {
    private static final Logger LOG = LoggerFactory.getLogger(OAuthProviderService.class);
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Value("${drivique.oauth.google.client-id:}") private String googleClientId;
    @Value("${drivique.oauth.facebook.app-id:}") private String facebookAppId;

    public OAuthProviderService(ObjectMapper mapper) { this.mapper = mapper; }

    public OAuthUserInfo verifyAndExtract(SocialLoginRequestDTO request) {
        return switch (request.normalizedProvider()) {
            case "GOOGLE" -> verifyGoogle(request);
            case "FACEBOOK" -> verifyFacebook(request);
            default -> throw new BadCredentialsException("Proveedor de identidad no soportado.");
        };
    }

    private OAuthUserInfo verifyGoogle(SocialLoginRequestDTO request) {
        String token = firstNonBlank(request.idToken(), request.accessToken(), request.authCode());
        if (blank(token)) throw new BadCredentialsException("Token de Google requerido.");

        try {
            JsonNode profile = null;
            if (looksLikeJwt(token)) {
                try {
                    // Google valida la firma del token ID
                    profile = getJson("https://oauth2.googleapis.com/tokeninfo?id_token=" + encode(token), null);
                } catch (Exception e) {
                    LOG.debug("No fue posible validar como id_token: {}", e.getMessage());
                }
            }
            if (profile == null) {
                // Si es un access_token (o id_token que no resolvió por tokeninfo), consultar endpoints oficiales
                try {
                    profile = getJson("https://www.googleapis.com/oauth2/v3/userinfo", token);
                } catch (Exception e) {
                    try {
                        profile = getJson("https://oauth2.googleapis.com/tokeninfo?access_token=" + encode(token), null);
                    } catch (Exception e2) {
                        profile = getJson("https://openidconnect.googleapis.com/v1/userinfo", token);
                    }
                }
            }
            return googleProfile(profile, request);
        } catch (BadCredentialsException bce) {
            throw bce;
        } catch (Exception providerFailure) {
            LOG.warn("Google token rejected: {}", providerFailure.getMessage());
            throw new BadCredentialsException("No fue posible validar el token con Google.");
        }
    }

    private OAuthUserInfo verifyFacebook(SocialLoginRequestDTO request) {
        String token = firstNonBlank(request.accessToken(), request.idToken(), request.authCode());
        if (blank(token)) throw new BadCredentialsException("Access Token de Facebook requerido.");

        try {
            JsonNode profile = getJson("https://graph.facebook.com/me?fields=id,email,first_name,last_name,name&access_token=" + encode(token), null);
            String id = text(profile, "id");
            if (blank(id)) throw new BadCredentialsException("Facebook no entregó un identificador válido.");
            String email = firstNonBlank(text(profile, "email"), request.email(), "fb." + id + "@facebook.drivique.com");
            String firstName = firstNonBlank(text(profile, "first_name"), request.firstName());
            String lastName = firstNonBlank(text(profile, "last_name"), request.lastName());
            return info("FACEBOOK", id, email, firstName, lastName, request.nonce());
        } catch (BadCredentialsException bce) {
            throw bce;
        } catch (Exception graphFailure) {
            LOG.warn("Facebook Graph API unavailable or token rejected: {}", graphFailure.getMessage());
            throw new BadCredentialsException("No fue posible validar el token con Facebook.");
        }
    }

    private OAuthUserInfo googleProfile(JsonNode profile, SocialLoginRequestDTO request) {
        validateGoogleClaims(profile, request);
        String sub = firstNonBlank(text(profile, "sub"), text(profile, "id"), text(profile, "user_id"));
        String email = firstNonBlank(text(profile, "email"), request.email());
        if (blank(sub) || blank(email)) throw new BadCredentialsException("Google no entregó un identificador y correo válidos.");
        if (profile.has("email_verified") && !"true".equalsIgnoreCase(profile.path("email_verified").asText())) {
            throw new BadCredentialsException("El correo de Google no está verificado.");
        }
        String firstName = firstNonBlank(text(profile, "given_name"), request.firstName());
        String lastName = firstNonBlank(text(profile, "family_name"), request.lastName());
        return info("GOOGLE", sub, email, firstName, lastName, request.nonce());
    }

    private void validateGoogleClaims(JsonNode payload, SocialLoginRequestDTO request) {
        String issuer = text(payload, "iss");
        if (!blank(issuer) && !GOOGLE_ISSUERS.contains(issuer)) throw new BadCredentialsException("Issuer de Google inválido.");
        long exp = payload.path("exp").asLong(0);
        if (exp > 0 && Instant.ofEpochSecond(exp).isBefore(Instant.now())) throw new BadCredentialsException("El token de Google expiró.");
        String audience = text(payload, "aud");
        if (!blank(googleClientId) && !blank(audience) && !googleClientId.equals(audience)) {
            String azp = text(payload, "azp");
            if (!googleClientId.equals(azp)) {
                throw new BadCredentialsException("Audiencia de Google incorrecta.");
            }
        }
        String nonce = text(payload, "nonce");
        if (!blank(request.nonce()) && !blank(nonce) && !request.nonce().equals(nonce)) throw new BadCredentialsException("Nonce de Google inválido.");
    }

    private JsonNode getJson(String url, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET();
        if (!blank(bearer)) builder.header("Authorization", "Bearer " + bearer);
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new BadCredentialsException("El proveedor rechazó el token.");
        return mapper.readTree(response.body());
    }

    private OAuthUserInfo info(String provider, String sub, String email, String first, String last, String nonce) {
        String cleanEmail = email.trim().toLowerCase();
        String[] derived = deriveNamesFromEmail(cleanEmail);

        String cleanFirst = first;
        String cleanLast = last;

        if (containsProvider(cleanFirst) || blank(cleanFirst)) {
            cleanFirst = derived[0];
        }
        if (containsProvider(cleanLast) || blank(cleanLast)) {
            cleanLast = derived[1];
        }

        return new OAuthUserInfo(provider, sub, cleanEmail, cleanFirst, cleanLast, nonce);
    }

    private static boolean containsProvider(String val) {
        if (blank(val)) return false;
        String lower = val.toLowerCase();
        return lower.contains("google") || lower.contains("facebook") || lower.contains("fb") || lower.contains("sandbox") || lower.contains("social") || lower.contains("meta");
    }

    private static String[] deriveNamesFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            return new String[]{"Usuario", ""};
        }
        String prefix = email.split("@")[0].replaceAll("[0-9]+$", "").trim();
        String[] parts = prefix.split("[._\\-\\s]+");
        String first = "Usuario";
        String last = "";
        if (parts.length > 0 && !parts[0].isBlank()) {
            first = capitalize(parts[0]);
        }
        if (parts.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isBlank()) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(capitalize(parts[i]));
                }
            }
            last = sb.toString();
        }
        return new String[]{first, last};
    }

    private static String capitalize(String str) {
        if (blank(str)) return "";
        return str.substring(0, 1).toUpperCase() + (str.length() > 1 ? str.substring(1).toLowerCase() : "");
    }

    private static boolean looksLikeJwt(String token) {
        if (blank(token)) return false;
        String trimmed = token.trim();
        return trimmed.startsWith("eyJ") && trimmed.split("\\.").length == 3;
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(JsonNode node, String field) { String value = node.path(field).asText(null); return blank(value) ? null : value; }
    private static String firstNonBlank(String... values) { for (String value : values) if (!blank(value)) return value.trim(); return null; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
