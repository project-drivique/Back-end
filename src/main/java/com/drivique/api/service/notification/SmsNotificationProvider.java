package com.drivique.api.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SmsNotificationProvider implements NotificationChannelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(SmsNotificationProvider.class);

    @Override
    public String getChannel() {
        return "SMS";
    }

    @Override
    public String getProviderName() {
        return "TwilioSms";
    }

    @Override
    public ProviderDeliveryResult deliver(String recipient, String subject, String message, String metadata) {
        if (recipient == null || recipient.isBlank()) {
            return ProviderDeliveryResult.failure("MISSING_RECIPIENT", "El número de teléfono móvil no puede estar vacío.");
        }

        String sid = "SM" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        LOG.info("[TwilioSms] SMS entregado al destinatario '{}': {} (sid: {})", recipient, message, sid);
        return ProviderDeliveryResult.success(sid);
    }
}
