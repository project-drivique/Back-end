package com.drivique.api.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailNotificationProvider implements NotificationChannelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationProvider.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailNotificationProvider(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @Override
    public String getChannel() {
        return "EMAIL";
    }

    @Override
    public String getProviderName() {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        return sender != null ? "JavaMailSender" : "MockEmailProvider";
    }

    @Override
    public ProviderDeliveryResult deliver(String recipient, String subject, String message, String metadata) {
        if (recipient == null || recipient.isBlank()) {
            return ProviderDeliveryResult.failure("MISSING_RECIPIENT", "El destinatario de correo no puede estar vacío.");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null) {
            try {
                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setTo(recipient);
                mailMessage.setSubject(subject);
                mailMessage.setText(message);
                mailSender.send(mailMessage);
                String messageId = "mail-" + UUID.randomUUID();
                LOG.info("Email entregado exitosamente a '{}' con id '{}'", recipient, messageId);
                return ProviderDeliveryResult.success(messageId);
            } catch (Exception e) {
                LOG.error("Fallo al entregar correo a '{}': {}", recipient, e.getMessage());
                return ProviderDeliveryResult.failure("SMTP_DELIVERY_ERROR", e.getMessage());
            }
        }

        String simulatedId = "mock-email-" + UUID.randomUUID();
        LOG.info("[MockEmailProvider] Correo simulado a '{}' [{}]: {}", recipient, subject, message);
        return ProviderDeliveryResult.success(simulatedId);
    }
}
