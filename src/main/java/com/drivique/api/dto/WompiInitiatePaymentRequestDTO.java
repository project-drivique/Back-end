package com.drivique.api.dto;
import jakarta.validation.constraints.*; import java.util.UUID;
public record WompiInitiatePaymentRequestDTO(@NotNull UUID contractId,@NotBlank @Pattern(regexp="CREDIT_CARD|DEBIT_CARD|PSE|NEQUI|BANCOLOMBIA") String paymentMethodCode) {}
