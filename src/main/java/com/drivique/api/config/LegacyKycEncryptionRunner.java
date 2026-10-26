package com.drivique.api.config;

import com.drivique.api.service.FileStorageService;
import com.drivique.api.service.LocalFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Al arrancar, cifra los documentos KYC que aún estén en claro (guardados antes del cifrado en reposo). */
@Component
public class LegacyKycEncryptionRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyKycEncryptionRunner.class);

    private final FileStorageService storage;

    public LegacyKycEncryptionRunner(FileStorageService storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!(storage instanceof LocalFileStorageService local)) {
            return;
        }
        try {
            int total = local.encryptLegacyKycFiles();
            if (total > 0) {
                LOG.info("[SEGURIDAD] {} documento(s) KYC que estaban en claro fueron cifrados con AES-256-GCM.", total);
            }
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo revisar el cifrado de documentos KYC: {}", ex.getMessage());
        }
    }
}
