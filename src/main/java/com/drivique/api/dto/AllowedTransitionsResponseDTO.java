package com.drivique.api.dto;

import java.util.List;
import java.util.UUID;

public record AllowedTransitionsResponseDTO(
        UUID reservationId,
        String currentStatus,
        List<String> allowedTransitions
) {}
