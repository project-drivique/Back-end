package com.drivique.api.kyc.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    String storeFile(MultipartFile file, String subDirectory);
    void validateFile(MultipartFile file);
}
