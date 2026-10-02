package com.drivique.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceQuoteResponseDTO(UUID vehicleId, int rentalDays, BigDecimal vehicleSubtotal,
        BigDecimal insuranceSubtotal, BigDecimal mileagePlanSubtotal, BigDecimal additionalServicesSubtotal,
        BigDecimal discountAmount, BigDecimal refundableSecurityDeposit, BigDecimal estimatedTotal, String couponCode) {}
