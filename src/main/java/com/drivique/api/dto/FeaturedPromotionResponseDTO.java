package com.drivique.api.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record FeaturedPromotionResponseDTO(UUID id,String code,String discountType,BigDecimal discountValue,Instant endsAt) {}
