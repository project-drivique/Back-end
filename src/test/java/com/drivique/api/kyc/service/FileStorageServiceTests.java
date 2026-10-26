package com.drivique.api.kyc.service;

import com.drivique.api.service.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTests {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString());
    }

    @Test
    void storeFileSucceedsWithValidJpeg() {
        MockMultipartFile file = new MockMultipartFile(
                "frontFile",
                "document_front.jpg",
                "image/jpeg",
                com.drivique.api.TestFiles.JPEG
        );

        String storedPath = storageService.storeFile(file, "kyc");

        assertThat(storedPath).startsWith("/uploads/kyc/");
        assertThat(storedPath).endsWith(".jpg");
    }

    @Test
    void storeFileSucceedsWithValidPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "document",
                "license.pdf",
                "application/pdf",
                "%PDF-1.4 mock pdf content".getBytes()
        );

        String storedPath = storageService.storeFile(file, "kyc");

        assertThat(storedPath).startsWith("/uploads/kyc/");
        assertThat(storedPath).endsWith(".pdf");
    }

    @Test
    void storeFileInKycEncryptsOnDiskAndDecryptsOnRead() throws Exception {
        byte[] originalContent = "%PDF-1.4 confidential citizen national ID".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "frontFile",
                "cedula.pdf",
                "application/pdf",
                originalContent
        );

        String storedPath = storageService.storeFile(file, "kyc");

        // Verificar que en el disco físico los bytes están cifrados (inician con el header DRV_ENC_GCM_V1)
        java.nio.file.Path physicalFile = tempDir.resolve("kyc").resolve(java.nio.file.Paths.get(storedPath).getFileName());
        byte[] rawDiskBytes = java.nio.file.Files.readAllBytes(physicalFile);
        assertThat(rawDiskBytes).isNotEqualTo(originalContent);
        assertThat(new String(rawDiskBytes, 0, 14, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("DRV_ENC_GCM_V1");

        // Al invocar read(storedPath), el servicio debe descifrarlo en memoria transparentemente
        byte[] readContent = storageService.read(storedPath);
        assertThat(readContent).isEqualTo(originalContent);
    }

    @Test
    void validateFileFailsWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "frontFile",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThatThrownBy(() -> storageService.validateFile(emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void validateFileFailsWhenMimeTypeIsInvalid() {
        MockMultipartFile textFile = new MockMultipartFile(
                "frontFile",
                "doc.txt",
                "text/plain",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> storageService.validateFile(textFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato no permitido");
    }

    @Test
    void validateFileFailsWhenSizeExceeds5Mb() {
        byte[] oversized = new byte[(int) (LocalFileStorageService.MAX_FILE_SIZE + 1024)];
        System.arraycopy(com.drivique.api.TestFiles.PNG, 0, oversized, 0, com.drivique.api.TestFiles.PNG.length);
        MockMultipartFile largeFile = new MockMultipartFile(
                "frontFile",
                "large.png",
                "image/png",
                oversized
        );

        assertThatThrownBy(() -> storageService.validateFile(largeFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5MB");
    }

    @Test
    void validateFileRejectsAnExecutableRenamedAsAnImage() {
        MockMultipartFile fake = new MockMultipartFile("frontFile", "cedula.jpg", "image/jpeg", "MZ-ejecutable-disfrazado".getBytes());

        assertThatThrownBy(() -> storageService.validateFile(fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void validateFileRejectsAFormatThatDoesNotMatchItsExtension() {
        MockMultipartFile pngAsPdf = new MockMultipartFile("frontFile", "licencia.pdf", "application/pdf", com.drivique.api.TestFiles.PNG);

        assertThatThrownBy(() -> storageService.validateFile(pngAsPdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no corresponde");
    }

    @Test
    void validateFileAcceptsRealJpegPngAndPdfSignatures() {
        storageService.validateFile(new MockMultipartFile("f", "a.jpg", "image/jpeg", com.drivique.api.TestFiles.JPEG));
        storageService.validateFile(new MockMultipartFile("f", "a.png", "image/png", com.drivique.api.TestFiles.PNG));
        storageService.validateFile(new MockMultipartFile("f", "a.pdf", "application/pdf", "%PDF-1.7 contenido".getBytes()));
    }
}
