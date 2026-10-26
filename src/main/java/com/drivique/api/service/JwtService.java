package com.drivique.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class JwtService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(String secret, long accessTokenExpirationSeconds, long refreshTokenExpirationDays) {
        this(secret, accessTokenExpirationSeconds, refreshTokenExpirationDays, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public JwtService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds,
            @Value("${jwt.refresh-token-expiration-days:7}") long refreshTokenExpirationDays,
            @Value("${app.security.require-secrets:false}") boolean requireSecrets
    ) {
        String effectiveSecret = SecuritySecrets.resolveOrEphemeral("JWT_SECRET", secret, requireSecrets, log);
        this.key = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public String generateAccessToken(
            UUID userId,
            String email,
            String name,
            List<String> roles,
            List<String> permissions,
            UUID branchId,
            String branchName
    ) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenExpirationSeconds);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", name)
                .claim("roles", roles != null ? roles : List.of())
                .claim("permissions", permissions != null ? permissions : List.of());

        if (branchId != null) {
            builder.claim("branchId", branchId.toString());
        }
        if (branchName != null) {
            builder.claim("branchName", branchName);
        }

        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(UUID userId, String email, String name, List<String> roles) {
        return generateAccessToken(userId, email, name, roles, List.of(), null, null);
    }

    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return "drivique_rf_" + HexFormat.of().formatHex(randomBytes);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public Claims parseAndValidate(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationDays() {
        return refreshTokenExpirationDays;
    }
}
