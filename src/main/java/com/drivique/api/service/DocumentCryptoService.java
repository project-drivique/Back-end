package com.drivique.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Servicio criptográfico para cifrado en reposo de documentos confidenciales KYC (AES-256-GCM).
 *
 * Estructura del payload cifrado en disco:
 * [MAGIC_HEADER (14 bytes)] + [IV (12 bytes)] + [Ciphertext + AuthTag (16 bytes)]
 *
 * El uso del magic header permite compatibilidad transparente y retroactiva con documentos legados
 * o pruebas previas sin romper la lectura.
 */
@Service
public class DocumentCryptoService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentCryptoService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final byte[] MAGIC_HEADER = "DRV_ENC_GCM_V1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH_BYTE = 12; // 96 bits recomendado para GCM
    private static final int TAG_LENGTH_BIT = 128; // 16 bytes de tag de autenticación
    /** Clave que estuvo versionada en el repositorio. Solo sirve para leer documentos guardados antes y para desarrollo local. */
    private static final String LEGACY_PUBLISHED_SECRET = "drivique-kyc-aes-256-gcm-master-key-secret-2026!";

    private final SecretKeySpec keySpec;
    private final SecretKeySpec legacyKeySpec;
    private final SecureRandom secureRandom;

    public DocumentCryptoService() {
        this("", false);
    }

    public DocumentCryptoService(String secretKey) {
        this(secretKey, false);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentCryptoService(
            @Value("${app.security.document-encryption-key:}") String secretKey,
            @Value("${app.security.require-secrets:false}") boolean requireSecrets
    ) {
        this.secureRandom = new SecureRandom();
        String effectiveKey;
        if (SecuritySecrets.isUsable(secretKey)) {
            effectiveKey = secretKey.trim();
        } else if (requireSecrets) {
            throw new IllegalStateException("[SEGURIDAD] APP_DOCUMENT_ENCRYPTION_KEY no está configurada o es insegura. "
                    + "Defínela como variable de entorno (mínimo " + SecuritySecrets.MIN_LENGTH + " caracteres aleatorios). Ej.: openssl rand -base64 48");
        } else {
            // Solo desarrollo local: así los documentos de prueba siguen legibles entre reinicios
            LOG.warn("[SEGURIDAD] APP_DOCUMENT_ENCRYPTION_KEY no está configurada: se usa una clave de desarrollo. No uses esto con datos reales.");
            effectiveKey = LEGACY_PUBLISHED_SECRET;
        }
        this.keySpec = deriveKey(effectiveKey);
        this.legacyKeySpec = LEGACY_PUBLISHED_SECRET.equals(effectiveKey) ? null : deriveKey(LEGACY_PUBLISHED_SECRET);
        LOG.info("DocumentCryptoService inicializado exitosamente con algoritmo AES-256-GCM.");
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Error al derivar clave criptográfica AES-256", e);
        }
    }

    /**
     * Cifra un arreglo de bytes en memoria utilizando AES-256-GCM.
     */
    public byte[] encrypt(byte[] plainBytes) {
        if (plainBytes == null || plainBytes.length == 0) {
            return plainBytes;
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] cipherBytes = cipher.doFinal(plainBytes);

            byte[] result = new byte[MAGIC_HEADER.length + IV_LENGTH_BYTE + cipherBytes.length];
            System.arraycopy(MAGIC_HEADER, 0, result, 0, MAGIC_HEADER.length);
            System.arraycopy(iv, 0, result, MAGIC_HEADER.length, IV_LENGTH_BYTE);
            System.arraycopy(cipherBytes, 0, result, MAGIC_HEADER.length + IV_LENGTH_BYTE, cipherBytes.length);

            return result;
        } catch (Exception e) {
            LOG.error("Error al cifrar documento con AES-256-GCM: {}", e.getMessage(), e);
            throw new IllegalStateException("Fallo en el cifrado seguro del documento: " + e.getMessage(), e);
        }
    }

    /**
     * Determina si el arreglo de bytes corresponde a un archivo cifrado por este servicio.
     */
    public boolean isEncrypted(byte[] data) {
        if (data == null || data.length < MAGIC_HEADER.length + IV_LENGTH_BYTE + 16) {
            return false;
        }
        for (int i = 0; i < MAGIC_HEADER.length; i++) {
            if (data[i] != MAGIC_HEADER[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Desencripta un archivo cifrado con AES-256-GCM. Si el archivo no tiene el formato
     * cifrado (documento legado previo), lo retorna intacto sin arrojar error.
     */
    public byte[] decrypt(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        if (!isEncrypted(data)) {
            // Archivo legado sin cifrado
            return data;
        }

        try {
            int ivStart = MAGIC_HEADER.length;
            int cipherStart = ivStart + IV_LENGTH_BYTE;

            byte[] iv = Arrays.copyOfRange(data, ivStart, cipherStart);
            byte[] cipherBytes = Arrays.copyOfRange(data, cipherStart, data.length);

            try {
                return decryptWith(keySpec, iv, cipherBytes);
            } catch (javax.crypto.AEADBadTagException wrongKey) {
                // Documentos guardados antes de rotar la clave: se leen con la clave anterior
                if (legacyKeySpec == null) {
                    throw wrongKey;
                }
                return decryptWith(legacyKeySpec, iv, cipherBytes);
            }
        } catch (Exception e) {
            LOG.error("Error al descifrar documento seguro con AES-256-GCM: {}", e.getMessage(), e);
            throw new IllegalStateException("No fue posible descifrar el documento confidencial: " + e.getMessage(), e);
        }
    }

    private byte[] decryptWith(SecretKeySpec key, byte[] iv, byte[] cipherBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        return cipher.doFinal(cipherBytes);
    }
}
