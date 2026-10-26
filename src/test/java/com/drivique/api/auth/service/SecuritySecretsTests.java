package com.drivique.api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drivique.api.service.DeliveryActionTokens;
import com.drivique.api.service.DocumentCryptoService;
import com.drivique.api.service.JwtService;
import com.drivique.api.service.SecuritySecrets;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SecuritySecretsTests {

    private static final String GOOD = "a-real-random-secret-with-more-than-32-characters-xyz";
    private static final String PUBLISHED_JWT = "driviqueSecureMasterAuthSigningSecretKeyHMAC512Sha2026GeneratedProductionSafe!";
    private static final String PUBLISHED_DOCS = "drivique-kyc-aes-256-gcm-master-key-secret-2026!";

    @Test
    void publishedOrShortSecretsAreNotUsable() {
        assertThat(SecuritySecrets.isUsable(null)).isFalse();
        assertThat(SecuritySecrets.isUsable("  ")).isFalse();
        assertThat(SecuritySecrets.isUsable("short")).isFalse();
        assertThat(SecuritySecrets.isUsable(PUBLISHED_JWT)).isFalse();
        assertThat(SecuritySecrets.isUsable(PUBLISHED_DOCS)).isFalse();
        assertThat(SecuritySecrets.isUsable(GOOD)).isTrue();
    }

    @Test
    void protectedEnvironmentRefusesToStartWithoutJwtSecret() {
        assertThatThrownBy(() -> new JwtService("", 900, 7, true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtService(PUBLISHED_JWT, 900, 7, true)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DeliveryActionTokens(null, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentFallsBackToEphemeralKeyThatCannotBeForgedWithPublishedOne() {
        JwtService dev = new JwtService("", 900, 7, false);
        JwtService attacker = new JwtService(GOOD, 900, 7, false);
        String forged = attacker.generateAccessToken(UUID.randomUUID(), "a@b.com", "A", java.util.List.of("SUPER_ADMIN"));
        assertThat(dev.parseAndValidate(forged)).isNull();
        assertThat(attacker.parseAndValidate(forged)).isNotNull();
    }

    @Test
    void deliveryLinksSignedWithOtherSecretAreRejected() {
        DeliveryActionTokens a = new DeliveryActionTokens(GOOD, true);
        DeliveryActionTokens b = new DeliveryActionTokens(GOOD + "-other", true);
        String token = a.create(UUID.randomUUID(), "PICKUP", "CONFIRM", Instant.now().plusSeconds(300));
        assertThat(a.parse(token)).isPresent();
        assertThat(b.parse(token)).isEmpty();
    }

    @Test
    void documentKeyIsMandatoryInProtectedEnvironments() {
        assertThatThrownBy(() -> new DocumentCryptoService("", true)).isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_DOCUMENT_ENCRYPTION_KEY");
        assertThatThrownBy(() -> new DocumentCryptoService(PUBLISHED_DOCS, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void documentsStoredWithTheOldKeyStayReadableAfterRotation() {
        byte[] original = "cedula".getBytes(StandardCharsets.UTF_8);
        byte[] storedBefore = new DocumentCryptoService(PUBLISHED_DOCS, false).encrypt(original);
        DocumentCryptoService rotated = new DocumentCryptoService(GOOD, true);

        assertThat(rotated.decrypt(storedBefore)).isEqualTo(original);
        assertThat(new DocumentCryptoService(GOOD, true).decrypt(rotated.encrypt(original))).isEqualTo(original);
    }
}
