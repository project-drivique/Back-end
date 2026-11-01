package com.drivique.api.dto;

import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record WompiInitiatePaymentRequestDTO(
        UUID contractId,
        String reservationReference,
        @Pattern(regexp = "CREDIT_CARD|DEBIT_CARD|PSE|NEQUI|BANCOLOMBIA|CARD|WOMPI") String paymentMethodCode
) {
    public WompiInitiatePaymentRequestDTO(UUID contractId, String paymentMethodCode) {
        this(contractId, null, paymentMethodCode);
    }
}
