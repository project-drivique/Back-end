package com.drivique.api.dto;

import java.math.BigDecimal;

/**
 * Desglose del valor de la reserva calculado por el servidor. El cargo administrativo y el IVA vienen vacíos
 * en las reservas creadas antes de que existieran (su total guardado no los incluye).
 */
public record ReservationPriceBreakdownDTO(
        BigDecimal insuranceSubtotal,
        BigDecimal mileageSubtotal,
        BigDecimal additionalServicesSubtotal,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal adminFee,
        BigDecimal adminFeeRate,
        BigDecimal vat,
        BigDecimal vatRate
) {}
