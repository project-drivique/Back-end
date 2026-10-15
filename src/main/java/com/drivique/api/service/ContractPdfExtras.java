package com.drivique.api.service;

/** Datos del contrato que no viven en la entidad del contrato y se resuelven al generar el PDF. */
public record ContractPdfExtras(String documentTypeName, String licenseStatusText, String paymentMethodCode) {
    public static final ContractPdfExtras EMPTY = new ContractPdfExtras(null, null, null);
}
