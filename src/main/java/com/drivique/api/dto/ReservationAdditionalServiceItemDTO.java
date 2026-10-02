package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationAdditionalServiceItemDTO(
        UUID additionalServiceId,
        String name,
        short quantity,
        BigDecimal dailyRate,
        BigDecimal subtotal
) {}
