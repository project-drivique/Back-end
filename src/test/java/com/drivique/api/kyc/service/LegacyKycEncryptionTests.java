package com.drivique.api.kyc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.drivique.api.service.DocumentCryptoService;
import com.drivique.api.service.LocalFileStorageService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyKycEncryptionTests {

    @TempDir
    Path uploads;

    @Test
    void plainKycFilesGetEncryptedOnceAndStayReadable() throws Exception {
        DocumentCryptoService crypto = new DocumentCryptoService("test-secret-key-for-unit-testing-32-bytes!!");
        LocalFileStorageService storage = new LocalFileStorageService(uploads.toString(), crypto);
        Path kyc = Files.createDirectories(uploads.resolve("kyc/user-1"));
        byte[] cedula = "contenido de la cedula".getBytes(StandardCharsets.UTF_8);
        Path legacy = Files.write(kyc.resolve("front.jpg"), cedula);
        Path alreadyEncrypted = Files.write(kyc.resolve("back.jpg"), crypto.encrypt(cedula));
        byte[] encryptedBefore = Files.readAllBytes(alreadyEncrypted);

        assertThat(storage.encryptLegacyKycFiles()).isEqualTo(1);

        assertThat(crypto.isEncrypted(Files.readAllBytes(legacy))).isTrue();
        assertThat(storage.read("/uploads/kyc/user-1/front.jpg")).isEqualTo(cedula);
        assertThat(Files.readAllBytes(alreadyEncrypted)).isEqualTo(encryptedBefore);
        // Idempotente: una segunda pasada no vuelve a cifrar nada
        assertThat(storage.encryptLegacyKycFiles()).isZero();
    }

    @Test
    void withoutKycFolderNothingHappens() {
        LocalFileStorageService storage = new LocalFileStorageService(uploads.toString(), new DocumentCryptoService("x".repeat(40)));
        assertThat(storage.encryptLegacyKycFiles()).isZero();
    }
}
