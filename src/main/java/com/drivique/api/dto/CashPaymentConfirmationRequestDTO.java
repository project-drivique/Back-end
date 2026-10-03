package com.drivique.api.dto;
import jakarta.validation.constraints.NotBlank;
public record CashPaymentConfirmationRequestDTO(@NotBlank String cashPaymentCode) {}
