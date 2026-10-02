package com.drivique.api.dto;
public record WompiCheckoutResponseDTO(String checkoutUrl,String publicKey,String currency,long amountInCents,String reference,String integritySignature) {}
