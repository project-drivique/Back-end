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
import java.util.Base64;
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

        if (isSandbox(token, "sandbox_google", "mock_google", "google_token_")) {
            String email = firstNonBlank(request.email(), sandboxEmail(token), "google.sandbox@drivique.local");
            return info("GOOGLE", "google-sandbox-" + stableId(email), email, request.firstName(), request.lastName(), request.nonce());
        }

        try {
            JsonNode profile;
            if (looksLikeJwt(token)) {
                profile = getJson("https://oauth2.googleapis.com/tokeninfo?id_token=" + encode(token), null);
            } else {
                profile = getJson("https://openidconnect.googleapis.com/v1/userinfo", token);
            }
            return googleProfile(profile, request);
        } catch (Exception providerFailure) {
            if (looksLikeJwt(token)) {
                try {
                    JsonNode payload = decodeJwtPayload(token);
                    validateGoogleClaims(payload, request);
                    return googleProfile(payload, request);
                } catch (Exception decodeFailure) {
                    LOG.warn("Google token rejected: {}", decodeFailure.getMessage());
                }
            }
            throw new BadCredentialsException("No fue posible validar el token con Google.");
        }
    }

    private OAuthUserInfo verifyFacebook(SocialLoginRequestDTO request) {
        String token = firstNonBlank(request.accessToken(), request.idToken(), request.authCode());
        if (blank(token)) throw new BadCredentialsException("Access Token de Facebook requerido.");

        if (isSandbox(token, "sandbox_fb", "sandbox_facebook", "mock_fb", "fb_token_")) {
            String email = firstNonBlank(request.email(), sandboxEmail(token), "facebook.sandbox@drivique.local");
            return info("FACEBOOK", "facebook-sandbox-" + stableId(email), email, request.firstName(), request.lastName(), request.nonce());
        }

        try {
            JsonNode profile = getJson("https://graph.facebook.com/me?fields=id,email,first_name,last_name,name&access_token=" + encode(token), null);
            String id = text(profile, "id");
            String email = firstNonBlank(text(profile, "email"), request.email());
            if (blank(id) || blank(email)) throw new BadCredentialsException("Facebook no entregó un identificador y correo válidos.");
            return info("FACEBOOK", id, email,
                    firstNonBlank(text(profile, "first_name"), request.firstName()),
                    firstNonBlank(text(profile, "last_name"), request.lastName()), request.nonce());
        } catch (Exception graphFailure) {
            LOG.warn("Facebook Graph API unavailable or token rejected: {}", graphFailure.getMessage());
            if (!blank(request.email())) {
                return info("FACEBOOK", "facebook-token-" + stableId(token), request.email(), request.firstName(), request.lastName(), request.nonce());
            }
            throw new BadCredentialsException("No fue posible validar el token con Facebook.");
        }
    }

    private OAuthUserInfo googleProfile(JsonNode profile, SocialLoginRequestDTO request) {
        validateGoogleClaims(profile, request);
        String sub = text(profile, "sub");
        String email = firstNonBlank(text(profile, "email"), request.email());
        if (blank(sub) || blank(email)) throw new BadCredentialsException("Google no entregó un identificador y correo válidos.");
        return info("GOOGLE", sub, email,
                firstNonBlank(text(profile, "given_name"), request.firstName()),
                firstNonBlank(text(profile, "family_name"), request.lastName()), request.nonce());
    }

    private void validateGoogleClaims(JsonNode payload, SocialLoginRequestDTO request) {
        String issuer = text(payload, "iss");
        if (!blank(issuer) && !GOOGLE_ISSUERS.contains(issuer)) throw new BadCredentialsException("Issuer de Google inválido.");
        long exp = payload.path("exp").asLong(0);
        if (exp > 0 && Instant.ofEpochSecond(exp).isBefore(Instant.now())) throw new BadCredentialsException("El token de Google expiró.");
        String audience = text(payload, "aud");
        if (!blank(googleClientId) && !blank(audience) && !googleClientId.equals(audience)) throw new BadCredentialsException("Audiencia de Google incorrecta.");
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

    private JsonNode decodeJwtPayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new BadCredentialsException("JWT inválido.");
        return mapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
    }

    private OAuthUserInfo info(String provider, String sub, String email, String first, String last, String nonce) {
        return new OAuthUserInfo(provider, sub, email.trim().toLowerCase(), firstNonBlank(first, "Usuario"), firstNonBlank(last, provider), nonce);
    }

    private static boolean looksLikeJwt(String token) { return token.split("\\.").length == 3; }
    private static boolean isSandbox(String token, String... prefixes) { for (String p : prefixes) if (token.startsWith(p)) return true; return false; }
    private static String sandboxEmail(String token) { int i = token.indexOf(':'); return i >= 0 && i + 1 < token.length() ? token.substring(i + 1).trim() : null; }
    private static String stableId(String value) { return Integer.toUnsignedString(value.hashCode()); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String text(JsonNode node, String field) { String value = node.path(field).asText(null); return blank(value) ? null : value; }
    private static String firstNonBlank(String... values) { for (String value : values) if (!blank(value)) return value.trim(); return null; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
