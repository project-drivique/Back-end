package com.drivique.api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cargos de la reserva calculados por el servidor: tarifa administrativa sobre el valor base y IVA sobre (base + tarifa).
 * No se aplican a recargos logísticos como el domicilio.
 */
@Component
public class ReservationPricing {

    private final BigDecimal adminFeeRate;
    private final BigDecimal vatRate;

    public ReservationPricing(@Value("${drivique.pricing.admin-fee-rate:0.10}") BigDecimal adminFeeRate,
                              @Value("${drivique.pricing.vat-rate:0.19}") BigDecimal vatRate) {
        this.adminFeeRate = adminFeeRate;
        this.vatRate = vatRate;
    }

    public record Breakdown(BigDecimal base, BigDecimal adminFee, BigDecimal vat, BigDecimal total) { }

    public BigDecimal adminFeeRate() { return adminFeeRate; }

    public BigDecimal vatRate() { return vatRate; }

    public Breakdown calculate(BigDecimal base, BigDecimal discount) {
        BigDecimal fee = base.multiply(adminFeeRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal vat = base.add(fee).multiply(vatRate).setScale(0, RoundingMode.HALF_UP);
        BigDecimal total = base.add(fee).add(vat).subtract(discount == null ? BigDecimal.ZERO : discount).max(BigDecimal.ZERO);
        return new Breakdown(base, fee, vat, total);
    }
}
