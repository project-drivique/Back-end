package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationPromotionItemDTO(
        UUID promotionId,
        String code,
        BigDecimal discountApplied
) {}
