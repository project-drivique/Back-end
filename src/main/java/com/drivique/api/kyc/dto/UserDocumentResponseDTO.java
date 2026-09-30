package com.drivique.api.kyc.dto;

import com.drivique.api.kyc.entity.UserDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserDocumentResponse", description = "Información del documento KYC del usuario")
public record UserDocumentResponseDTO(
        UUID id,
        UUID userId,
        DocumentTypeResponseDTO documentType,
        String documentNumber,
        String frontUrl,
        String backUrl,
        String statusCode,
        String statusName,
        String reviewNotes,
        UUID reviewedById,
        String reviewedByName,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserDocumentResponseDTO fromEntity(UserDocument doc) {
        if (doc == null) return null;
        return new UserDocumentResponseDTO(
                doc.getId(),
                doc.getUser() != null ? doc.getUser().getId() : null,
                DocumentTypeResponseDTO.fromEntity(doc.getDocumentType()),
                doc.getDocumentNumber(),
                doc.getFrontUrl(),
                doc.getBackUrl(),
                doc.getStatus() != null ? doc.getStatus().getCode() : null,
                doc.getStatus() != null ? doc.getStatus().getName() : null,
                doc.getReviewNotes(),
                doc.getReviewedBy() != null ? doc.getReviewedBy().getId() : null,
                doc.getReviewedBy() != null ? doc.getReviewedBy().getFullName() : null,
                doc.getReviewedAt(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
