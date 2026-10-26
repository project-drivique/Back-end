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
    private final DocumentCryptoService documentCryptoService;

    public LocalFileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this(uploadDir, new DocumentCryptoService());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public LocalFileStorageService(
            @Value("${app.upload.dir:uploads}") String uploadDir,
            DocumentCryptoService documentCryptoService
    ) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.documentCryptoService = documentCryptoService != null ? documentCryptoService : new DocumentCryptoService();
    }

    private boolean isKycDirectory(String subDirectory) {
        if (subDirectory == null) return false;
        String normalized = subDirectory.toLowerCase(Locale.ROOT).replace('\\', '/');
        return normalized.equals("kyc") || normalized.startsWith("kyc/") || normalized.contains("/kyc/");
    }

    /**
     * Cifra en el lugar los documentos KYC que se guardaron antes de existir el cifrado en reposo.
     * Es idempotente: los archivos que ya están cifrados no se tocan. Devuelve cuántos cifró.
     */
    public int encryptLegacyKycFiles() {
        Path kycDir = rootLocation.resolve("kyc").normalize();
        if (!Files.isDirectory(kycDir)) {
            return 0;
        }
        int encrypted = 0;
        try (java.util.stream.Stream<Path> walk = Files.walk(kycDir)) {
            for (Path file : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                try {
                    byte[] raw = Files.readAllBytes(file);
                    if (raw.length == 0 || documentCryptoService.isEncrypted(raw)) {
                        continue;
                    }
                    Path temp = file.resolveSibling(file.getFileName() + ".enc-tmp");
                    Files.write(temp, documentCryptoService.encrypt(raw));
                    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    encrypted++;
                } catch (IOException | RuntimeException ex) {
                    // Un archivo problemático no debe impedir que se cifren los demás
                    org.slf4j.LoggerFactory.getLogger(LocalFileStorageService.class)
                            .warn("No se pudo cifrar el documento legado {}: {}", file.getFileName(), ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible recorrer los documentos KYC.", ex);
        }
        return encrypted;
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

            if (isKycDirectory(subDirectory)) {
                byte[] plainBytes = file.getBytes();
                byte[] encryptedBytes = documentCryptoService.encrypt(plainBytes);
                Files.write(destinationFile, encryptedBytes);
            } else {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
                }
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
            byte[] bytesToWrite = isKycDirectory(subDirectory) ? documentCryptoService.encrypt(content) : content;
            Files.write(destination, bytesToWrite);
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
            byte[] bytesToWrite = isKycDirectory(subDirectory) ? documentCryptoService.encrypt(content) : content;
            Files.write(destination, bytesToWrite);
            return "/uploads/" + subDirectory + "/" + destination.getFileName();
        } catch (IOException e) {
            throw new IllegalStateException("Error al almacenar el archivo generado.", e);
        }
    }

    @Override
    public byte[] read(String storedPath) {
        if (storedPath == null || !storedPath.startsWith("/uploads/")) throw new IllegalArgumentException("Ruta de archivo inválida.");
        Path file = rootLocation.resolve(storedPath.substring("/uploads/".length())).normalize();
        if (!file.startsWith(rootLocation) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Archivo no encontrado.");
        try {
            byte[] rawBytes = Files.readAllBytes(file);
            if (isKycDirectory(storedPath) || documentCryptoService.isEncrypted(rawBytes)) {
                return documentCryptoService.decrypt(rawBytes);
            }
            return rawBytes;
        } catch (IOException ex) {
            throw new IllegalStateException("No fue posible leer el archivo.", ex);
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
