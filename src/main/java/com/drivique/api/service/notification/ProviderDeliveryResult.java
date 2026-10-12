package com.drivique.api.service.notification;

public record ProviderDeliveryResult(
        boolean success,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {
    public static ProviderDeliveryResult success(String providerMessageId) {
        return new ProviderDeliveryResult(true, providerMessageId, null, null);
    }

    public static ProviderDeliveryResult failure(String errorCode, String errorMessage) {
        return new ProviderDeliveryResult(false, null, errorCode, SecretSanitizer.sanitize(errorMessage));
    }
}
