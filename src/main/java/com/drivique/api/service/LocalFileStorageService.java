package com.drivique.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService implements FileStorageService {

    public static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "application/pdf"
    );
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".pdf"
    );

    private final Path rootLocation;

    public LocalFileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio y no puede estar vacío.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El tamaño del archivo excede el límite máximo permitido de 5MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato no permitido. Solo se aceptan archivos JPG, PNG y PDF.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Extensión de archivo no permitida. Solo se aceptan extensiones .jpg, .jpeg, .png y .pdf.");
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = getFileExtension(originalFilename);
        String uniqueFilename = UUID.randomUUID() + extension;

        try {
            Path targetDir = this.rootLocation.resolve(subDirectory).normalize();
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            Path destinationFile = targetDir.resolve(uniqueFilename).normalize();
            if (!destinationFile.toAbsolutePath().startsWith(this.rootLocation.toAbsolutePath())) {
                throw new IllegalArgumentException("No se permite almacenar archivos fuera del directorio designado.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return "/uploads/" + subDirectory + "/" + uniqueFilename;
        } catch (IOException e) {
            throw new IllegalStateException("Error al almacenar el archivo en disco: " + e.getMessage(), e);
        }
    }

    @Override
    public String storePdf(byte[] content, String subDirectory) {
        if (content == null || content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El PDF generado no es válido o excede el límite de 5MB.");
        }
        try {
            Path targetDir = rootLocation.resolve(subDirectory).normalize();
            Files.createDirectories(targetDir);
            Path destination = targetDir.resolve(UUID.randomUUID() + ".pdf").normalize();
            if (!destination.toAbsolutePath().startsWith(rootLocation.toAbsolutePath())) {
                throw new IllegalArgumentException("No se permite almacenar archivos fuera del directorio designado.");
            }
            Files.write(destination, content);
            return "/uploads/" + subDirectory + "/" + destination.getFileName();
        } catch (IOException e) {
            throw new IllegalStateException("Error al almacenar el PDF generado.", e);
        }
    }

    @Override
    public String storeBytes(byte[] content, String filenameWithExtension, String subDirectory) {
        if (content == null || content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El archivo generado no es válido o excede el límite de 5MB.");
        }
        try {
            Path targetDir = rootLocation.resolve(subDirectory).normalize();
            Files.createDirectories(targetDir);
            String safeName = UUID.randomUUID() + "_" + StringUtils.cleanPath(filenameWithExtension);
            Path destination = targetDir.resolve(safeName).normalize();
            if (!destination.toAbsolutePath().startsWith(rootLocation.toAbsolutePath())) {
                throw new IllegalArgumentException("No se permite almacenar archivos fuera del directorio designado.");
            }
            Files.write(destination, content);
            return "/uploads/" + subDirectory + "/" + destination.getFileName();
        } catch (IOException e) {
            throw new IllegalStateException("Error al almacenar el archivo generado.", e);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return "";
    }
}
