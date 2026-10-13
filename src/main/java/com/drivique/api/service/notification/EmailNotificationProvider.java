package com.drivique.api.service.notification;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EmailNotificationProvider implements NotificationChannelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(EmailNotificationProvider.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String fromAddress;

    @Autowired
    public EmailNotificationProvider(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.mail-from:no-reply@drivique.com}") String fromAddress
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
    }

    public EmailNotificationProvider(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this(mailSenderProvider, "no-reply@drivique.com");
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
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom(new InternetAddress(fromAddress, "Drivique"));
                helper.setTo(recipient);
                helper.setSubject(subject != null ? subject : "Notificación Drivique");

                String safeSubject = subject != null ? subject : "Notificación Drivique";
                String safeMessage = message != null ? message : "";

                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="UTF-8"></head>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                      <div style="max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 26px 30px; text-align: center;">
                          <h1 style="color: #ffffff; margin: 0; font-size: 22px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                          <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">Notificaciones de tu cuenta y reservas</p>
                        </div>
                        <div style="padding: 28px 30px 24px;">
                          <h2 style="color: #0f172a; margin: 0 0 14px; font-size: 17px; font-weight: 700;">{{SUBJECT}}</h2>
                          <div style="color: #334155; font-size: 14px; line-height: 1.6; margin: 0 0 20px; white-space: pre-line;">
                            {{MESSAGE}}
                          </div>
                          <div style="border-top: 1px solid #e2e8f0; padding-top: 18px; margin-top: 20px;">
                            <p style="color: #94a3b8; font-size: 12px; margin: 0;">
                              Este es un mensaje automático del sistema de notificaciones de Drivique. Puedes gestionar tus canales desde tu perfil.
                            </p>
                          </div>
                        </div>
                      </div>
                    </body>
                    </html>
                    """
                        .replace("{{SUBJECT}}", safeSubject)
                        .replace("{{MESSAGE}}", safeMessage);

                helper.setText(safeMessage, html);
                mailSender.send(mimeMessage);

                String messageId = "mail-" + UUID.randomUUID();
                LOG.info("Email entregado exitosamente vía JavaMailSender a '{}' con id '{}'", recipient, messageId);
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
