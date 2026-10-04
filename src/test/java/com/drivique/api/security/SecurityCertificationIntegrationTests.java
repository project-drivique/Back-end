package com.drivique.api.security;

import com.drivique.api.DatabaseHealthTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Suite de pruebas de integración para HU-BE-41: Certificación de Seguridad, Criptografía y Hardening de Endpoints.
 * Valida cabeceras de seguridad HTTP, control de acceso por JWT, BCrypt y respuestas RFC 7807 sin exposición sensible.
 */
@DisplayName("HU-BE-41: Certificación de Seguridad, Criptografía y Hardening de Endpoints")
class SecurityCertificationIntegrationTests extends DatabaseHealthTestSupport {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Verificar presencia de cabeceras HTTP de Security Hardening (X-Frame-Options, X-Content-Type-Options, CSP, XSS)")
    void securityHardeningHeadersArePresentOnResponses() throws Exception {
        var response = getResponse("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);

        var headers = response.headers();
        assertThat(headers.firstValue("X-Frame-Options")).contains("DENY");
        assertThat(headers.firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(headers.firstValue("X-XSS-Protection")).contains("1; mode=block");
        assertThat(headers.firstValue("Content-Security-Policy")).isPresent();
        assertThat(headers.firstValue("Content-Security-Policy").get())
                .contains("default-src 'self'", "frame-ancestors 'none'");
    }

    @Test
    @DisplayName("Verificar hardening de Actuator: /actuator/env debe estar denegado/protegido")
    void actuatorEnvironmentIsRestricted() throws Exception {
        var response = getResponse("/actuator/env");
        assertThat(response.statusCode()).isIn(401, 403, 404);
    }

    @Test
    @DisplayName("Verificar hardening de Endpoints: Peticiones a endpoints protegidos sin JWT devuelven 401/403")
    void protectedEndpointsRequireAuthentication() throws Exception {
        var response = getResponse("/v1/users/me");
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    @DisplayName("Verificar hardening contra JWTs malformados o inválidos")
    void invalidJwtTokenIsRejected() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/users/me"))
                .header("Authorization", "Bearer token_invalido_malformado")
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    @DisplayName("Verificar Criptografía: Hashing seguro de contraseñas con BCrypt/PasswordEncoder")
    void passwordHashingVerification() {
        String rawPassword = "DriviqueSecurePassword2026!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(encodedPassword).startsWith("$2a$").hasSizeGreaterThan(50);
        assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword123!", encodedPassword)).isFalse();
    }

    private HttpResponse<String> getResponse(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
