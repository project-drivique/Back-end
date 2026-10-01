package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record VehicleDocumentRequestDTO(
        @NotBlank(message = "Document type is required")
        @Pattern(regexp = "SOAT|TECHNICAL_INSPECTION|REGISTRATION|INSURANCE|OTHER", message = "Invalid document type. Allowed: SOAT, TECHNICAL_INSPECTION, REGISTRATION, INSURANCE, OTHER")
        String documentType,

        @Size(max = 100, message = "Document number cannot exceed 100 characters")
        String documentNumber,

        @NotBlank(message = "File URL is required")
        @Size(max = 1000, message = "File URL cannot exceed 1000 characters")
        String fileUrl,

        LocalDate issuedAt,

        LocalDate expiresAt
) {}
