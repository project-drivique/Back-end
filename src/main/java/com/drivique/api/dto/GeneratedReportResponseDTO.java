package com.drivique.api.dto;

import com.drivique.api.model.GeneratedReport;
import java.time.Instant;
import java.util.UUID;

public record GeneratedReportResponseDTO(
        UUID id,
        String reportTypeCode,
        String reportTypeName,
        String format,
        UUID generatedById,
        String generatedByName,
        String filters,
        String fileUrl,
        String status,
        Instant generatedAt,
        Instant createdAt
) {
    public static GeneratedReportResponseDTO fromEntity(GeneratedReport report) {
        return new GeneratedReportResponseDTO(
                report.getId(),
                report.getReportType() != null ? report.getReportType().getCode() : null,
                report.getReportType() != null ? report.getReportType().getName() : null,
                report.getFormat(),
                report.getGeneratedBy() != null ? report.getGeneratedBy().getId() : null,
                report.getGeneratedBy() != null ? report.getGeneratedBy().getFullName() : null,
                report.getFilters(),
                report.getFileUrl(),
                report.getStatus(),
                report.getGeneratedAt(),
                report.getCreatedAt()
        );
    }
}
