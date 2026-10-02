package com.drivique.api.dto;
import java.math.BigDecimal; import java.util.UUID;
public record PromotionValidationResponseDTO(UUID promotionId,String code,String discountType,BigDecimal discountValue,BigDecimal discountAmount,BigDecimal baseAmount) {}
