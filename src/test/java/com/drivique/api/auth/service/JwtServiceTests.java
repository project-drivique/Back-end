package com.drivique.api.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTests {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                "driviqueSuperSecretKeyForJwtAuthenticationMustBeAtLeast256BitsLong2026!",
                900,
                7
        );
    }

    @Test
    void generatesAndValidatesAccessToken() {
        UUID userId = UUID.randomUUID();
        String email = "test@drivique.com";
        String name = "Test User";
        List<String> roles = List.of("CUSTOMER", "EMPLOYEE");

        String token = jwtService.generateAccessToken(userId, email, name, roles);
        assertThat(token).isNotEmpty();

        Claims claims = jwtService.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo(email);
        assertThat(claims.get("name", String.class)).isEqualTo(name);
        assertThat(claims.get("roles")).isInstanceOf(List.class);
    }

    @Test
    void rejectsInvalidOrTamperedToken() {
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalidPayload.invalidSignature";
        Claims claims = jwtService.parseAndValidate(invalidToken);
        assertThat(claims).isNull();
    }

    @Test
    void generatesSecureRefreshTokenAndHash() {
        String refreshToken = jwtService.generateRefreshToken();
        assertThat(refreshToken).startsWith("drivique_rf_");

        String hash1 = jwtService.hashToken(refreshToken);
        String hash2 = jwtService.hashToken(refreshToken);
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 hex string
    }
}
