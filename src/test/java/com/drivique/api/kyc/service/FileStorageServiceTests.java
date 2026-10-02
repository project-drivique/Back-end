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
                "sample-image-content".getBytes()
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
}
