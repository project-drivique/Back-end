package com.drivique.api.dto;

import java.time.Instant;
import java.util.List;

public record ExpiredReservationsSummaryDTO(
        int expiredCount,
        List<String> expiredReservationCodes,
        Instant executedAt
) {
}
