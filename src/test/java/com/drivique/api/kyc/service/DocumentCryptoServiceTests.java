package com.drivique.api.kyc.service;

import com.drivique.api.service.DocumentCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentCryptoServiceTests {

    private DocumentCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new DocumentCryptoService("test-secret-key-for-unit-testing-32-bytes!!");
    }

    @Test
    void encryptAndDecryptPdfContentSucceeds() {
        byte[] originalPdf = "%PDF-1.4 Mock sensitive government ID PDF content".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cryptoService.encrypt(originalPdf);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(originalPdf);
        assertThat(cryptoService.isEncrypted(encrypted)).isTrue();

        byte[] decrypted = cryptoService.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(originalPdf);
    }

    @Test
    void encryptProducesDifferentCiphertextForSamePlaintextDueToRandomIv() {
        byte[] data = "sample user document bytes".getBytes(StandardCharsets.UTF_8);

        byte[] enc1 = cryptoService.encrypt(data);
        byte[] enc2 = cryptoService.encrypt(data);

        // Different IVs mean ciphertexts differ
        assertThat(enc1).isNotEqualTo(enc2);

        // But both decrypt back to the original plaintext
        assertThat(cryptoService.decrypt(enc1)).isEqualTo(data);
        assertThat(cryptoService.decrypt(enc2)).isEqualTo(data);
    }

    @Test
    void decryptReturnsLegacyPlaintextWhenNotEncrypted() {
        byte[] legacyPlaintext = "Legacy plain photo without magic header".getBytes(StandardCharsets.UTF_8);

        assertThat(cryptoService.isEncrypted(legacyPlaintext)).isFalse();

        byte[] result = cryptoService.decrypt(legacyPlaintext);

        assertThat(result).isEqualTo(legacyPlaintext);
    }

    @Test
    void decryptFailsWhenCiphertextIsTampered() {
        byte[] original = "Confidential citizen document".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cryptoService.encrypt(original);

        // Tamper with the last byte (auth tag)
        encrypted[encrypted.length - 1] ^= 0x5A;

        assertThatThrownBy(() -> cryptoService.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No fue posible descifrar");
    }

    @Test
    void nullOrEmptyHandling() {
        assertThat(cryptoService.encrypt(null)).isNull();
        assertThat(cryptoService.encrypt(new byte[0])).isEmpty();
        assertThat(cryptoService.decrypt(null)).isNull();
        assertThat(cryptoService.decrypt(new byte[0])).isEmpty();
        assertThat(cryptoService.isEncrypted(null)).isFalse();
        assertThat(cryptoService.isEncrypted(new byte[0])).isFalse();
    }
}
