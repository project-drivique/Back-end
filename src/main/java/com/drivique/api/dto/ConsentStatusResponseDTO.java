package com.drivique.api.dto;
import java.util.List;
public record ConsentStatusResponseDTO(boolean requirementsConfigured, boolean hasPendingConsents,
        List<ConsentRequirementDTO> pendingConsents) {}
