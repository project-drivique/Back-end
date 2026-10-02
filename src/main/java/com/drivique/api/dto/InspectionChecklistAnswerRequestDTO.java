package com.drivique.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record InspectionChecklistAnswerRequestDTO(@NotNull UUID checklistItemId, boolean compliant, @Size(max = 4000) String observation, Integer evidencePhotoIndex) {}
