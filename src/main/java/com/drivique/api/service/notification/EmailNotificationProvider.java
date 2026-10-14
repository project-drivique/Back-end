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
    private final String frontendUrl;
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    @Autowired
    public EmailNotificationProvider(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${app.auth.mail-from:no-reply@drivique.com}") String fromAddress,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    public EmailNotificationProvider(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this(mailSenderProvider, "no-reply@drivique.com", "http://localhost:5173");
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

                String actionLabel = null;
                String actionUrl = null;
                if (metadata != null && !metadata.isBlank()) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(metadata);
                        if (root.hasNonNull("actionLabel")) {
                            actionLabel = root.get("actionLabel").asText();
                        }
                        if (root.hasNonNull("actionUrl")) {
                            actionUrl = root.get("actionUrl").asText();
                        } else if (root.hasNonNull("actionPath")) {
                            String path = root.get("actionPath").asText();
                            String base = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
                            actionUrl = base + (path.startsWith("/") ? "" : "/") + path;
                        }
                    } catch (Exception e) {
                        LOG.debug("No se pudo parsear metadata en EmailNotificationProvider: {}", e.getMessage());
                    }
                }

                String actionButtonHtml = "";
                String textMessage = safeMessage;
                if (actionUrl != null && !actionUrl.isBlank()) {
                    String label = (actionLabel != null && !actionLabel.isBlank()) ? actionLabel : "Continuar";
                    actionButtonHtml = """
                        <div style="text-align: center; margin: 26px 0 20px;">
                          <a href="{{ACTION_URL}}" style="display: inline-block; background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); color: #ffffff; text-decoration: none; font-size: 14px; font-weight: 700; padding: 13px 26px; border-radius: 10px; box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);">{{ACTION_LABEL}}</a>
                        </div>
                        """
                        .replace("{{ACTION_URL}}", actionUrl)
                        .replace("{{ACTION_LABEL}}", label);
                    textMessage = safeMessage + "\n\n" + label + ": " + actionUrl;
                }

                String html = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="UTF-8"></head>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                      <div style="max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;">
                          <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                          <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">Tu libertad sobre ruedas comienza aquí</p>
                        </div>
                        <div style="padding: 28px 30px 24px;">
                          <h2 style="color: #0f172a; margin: 0 0 14px; font-size: 17px; font-weight: 700;">{{SUBJECT}}</h2>
                          <div style="color: #334155; font-size: 14px; line-height: 1.6; margin: 0 0 20px; white-space: pre-line;">
                            {{MESSAGE}}
                          </div>
                          {{ACTION_BUTTON}}
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
                        .replace("{{MESSAGE}}", safeMessage)
                        .replace("{{ACTION_BUTTON}}", actionButtonHtml);

                helper.setText(textMessage, html);
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
