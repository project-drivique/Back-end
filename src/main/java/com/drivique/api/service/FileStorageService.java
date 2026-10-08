package com.drivique.api.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeFile(MultipartFile file, String subDirectory);
    String storePdf(byte[] content, String subDirectory);
    String storeBytes(byte[] content, String filenameWithExtension, String subDirectory);
    void validateFile(MultipartFile file);
    byte[] read(String storedPath);
}
