package com.drivique.api.dto;
import jakarta.validation.constraints.*;
public record SaveWompiPaymentMethodRequestDTO(@NotBlank @Size(max=255) String paymentToken,@NotBlank @Size(max=50) String cardBrand,@Pattern(regexp="\\d{4}") String lastFour,@Min(1) @Max(12) short expMonth,@Min(2024) short expYear) {}
