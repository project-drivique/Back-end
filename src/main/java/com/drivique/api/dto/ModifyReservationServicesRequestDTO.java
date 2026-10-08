package com.drivique.api.dto;

import java.util.List;
import java.util.UUID;

public record ModifyReservationServicesRequestDTO(
        List<UUID> additionalServiceIds,
        UUID insuranceCoverageId,
        UUID mileagePlanId
) {}
