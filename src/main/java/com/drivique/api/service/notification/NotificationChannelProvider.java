package com.drivique.api.service.notification;

public interface NotificationChannelProvider {

    String getChannel();

    String getProviderName();

    ProviderDeliveryResult deliver(String recipient, String subject, String message, String metadata);
}
