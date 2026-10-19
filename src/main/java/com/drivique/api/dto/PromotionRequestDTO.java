package com.drivique.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PromotionRequestDTO(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,24}$", message = "El código debe tener entre 3 y 24 caracteres (letras, números, guion o guion bajo)") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank String conditions,
        @NotBlank @Pattern(regexp = "COUPON|PROMOTION", message = "Tipo de oferta inválido") String offerType,
        @NotBlank @Pattern(regexp = "PERCENTAGE|FIXED_AMOUNT", message = "Tipo de descuento inválido") String discountType,
        @NotNull @DecimalMin(value = "0.01", message = "El valor del descuento debe ser mayor a cero") BigDecimal discountValue,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @DecimalMin("0") BigDecimal minimumAmount,
        @Min(1) Integer minimumRentalDays,
        @Min(1) Integer maxUsesLimit,
        UUID vehicleCategoryId,
        UUID vehicleId,
        @Pattern(regexp = "ALL|NEW|FREQUENT", message = "Audiencia inválida") String audience,
        Boolean featured,
        Boolean active,
        UUID branchId
) {}
