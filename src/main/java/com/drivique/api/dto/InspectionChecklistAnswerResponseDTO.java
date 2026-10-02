package com.drivique.api.dto;

import java.util.UUID;

public record InspectionChecklistAnswerResponseDTO(UUID checklistItemId, String checklistItemName, boolean compliant, String observation, String evidencePhotoUrl) {}
