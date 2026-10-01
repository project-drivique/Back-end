package com.drivique.api.dto;

import java.util.UUID;

public record VehicleStatusUpdateRequestDTO(
        UUID statusId,
        String statusCode
) {}
