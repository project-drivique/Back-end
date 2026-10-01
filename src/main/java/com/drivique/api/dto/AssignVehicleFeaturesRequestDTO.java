package com.drivique.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AssignVehicleFeaturesRequestDTO(
        @NotEmpty(message = "Feature IDs list cannot be empty")
        List<UUID> featureIds
) {}
