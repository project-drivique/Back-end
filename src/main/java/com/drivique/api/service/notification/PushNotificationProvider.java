package com.drivique.api.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PushNotificationProvider implements NotificationChannelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(PushNotificationProvider.class);

    @Override
    public String getChannel() {
        return "PUSH";
    }

    @Override
    public String getProviderName() {
        return "FirebaseCloudMessaging";
    }

    @Override
    public ProviderDeliveryResult deliver(String recipient, String subject, String message, String metadata) {
        if (recipient == null || recipient.isBlank()) {
            return ProviderDeliveryResult.failure("MISSING_RECIPIENT", "El identificador o token del destinatario push es obligatorio.");
        }

        String fcmMessageId = "fcm-" + UUID.randomUUID();
        LOG.info("[FirebaseCloudMessaging] Push notification enviada a '{}' [{}]: {} (id: {})",
                recipient, subject, message, fcmMessageId);
        return ProviderDeliveryResult.success(fcmMessageId);
    }
}
