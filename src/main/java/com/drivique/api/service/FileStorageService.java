package com.drivique.api.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeFile(MultipartFile file, String subDirectory);
    void validateFile(MultipartFile file);
}
