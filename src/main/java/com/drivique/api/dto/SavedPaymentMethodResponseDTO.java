package com.drivique.api.dto;
import java.util.UUID;
public record SavedPaymentMethodResponseDTO(UUID id,String cardBrand,String lastFour,short expMonth,short expYear) {}
