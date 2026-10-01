package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ReviewDocumentRequest", description = "Solicitud para auditar y cambiar estado de documento KYC")
public record ReviewDocumentRequestDTO(
        @NotBlank(message = "El estado de revisión es obligatorio")
        @Pattern(regexp = "^(APPROVED|REJECTED)$", message = "El estado debe ser APPROVED o REJECTED")
        @Schema(example = "APPROVED", allowableValues = {"APPROVED", "REJECTED"})
        String status,

        @Size(max = 500, message = "Las notas de revisión no pueden exceder 500 caracteres")
        @Schema(example = "Documento verificado y legible con datos correctos.")
        String reviewNotes
) {}
