package com.drivique.api.dto;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record CashPaymentConfirmationResponseDTO(UUID paymentId,String reference,BigDecimal amount,String status,Instant confirmedAt) {}
