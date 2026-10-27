package com.drivique.api.service.notification;

import com.drivique.api.service.email.EmailLanguage;
import com.drivique.api.service.email.EmailTexts;
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

import java.util.Locale;
import java.util.UUID;

@Component
public class EmailNotificationProvider implements NotificationChannelProvider {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.drivique.api.service.BrandEmailTheme brandTheme;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailLanguage emailLanguage;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailTexts emailTexts;

    private static final EmailTexts DEFAULT_TEXTS = new EmailTexts();

    private String brand(String html) {
        return brandTheme == null ? html : brandTheme.apply(html);
    }


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
        return sender != null ? "JavaMailSender" : "NotConfigured";
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

                EmailTexts texts = emailTexts != null ? emailTexts : DEFAULT_TEXTS;
                Locale locale = emailLanguage != null ? emailLanguage.forEmail(recipient) : EmailLanguage.DEFAULT;
                String defaultSubject = texts.get(locale, "common.defaultSubject");

                helper.setFrom(new InternetAddress(fromAddress, texts.brandName()));
                helper.setTo(recipient);
                helper.setSubject(subject != null ? subject : defaultSubject);

                String safeSubject = subject != null ? subject : defaultSubject;
                String safeMessage = message != null ? message : "";

                String actionLabel = null;
                String actionUrl = null;
                String paymentStatus = null;
                String bookingCode = null;
                String amount = null;
                String currency = null;

                String paymentMethod = null;
                boolean reviewInvite = false;
                if (metadata != null && !metadata.isBlank()) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(metadata);
                        if (root.hasNonNull("actionLabel")) {
                            actionLabel = texts.actionLabel(locale, root.get("actionLabel").asText());
                        }
                        if (root.hasNonNull("actionUrl")) {
                            actionUrl = root.get("actionUrl").asText();
                        } else if (root.hasNonNull("actionPath")) {
                            String path = root.get("actionPath").asText();
                            String base = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
                            actionUrl = base + (path.startsWith("/") ? "" : "/") + path;
                        }
                        if (root.hasNonNull("status")) {
                            paymentStatus = root.get("status").asText();
                        }
                        if (root.hasNonNull("bookingCode")) {
                            bookingCode = root.get("bookingCode").asText();
                        }
                        if (root.hasNonNull("amount")) {
                            amount = root.get("amount").asText();
                        }
                        if (root.hasNonNull("currency")) {
                            currency = root.get("currency").asText();
                        }
                        if (root.hasNonNull("paymentMethod")) {
                            paymentMethod = root.get("paymentMethod").asText();
                        }
                        reviewInvite = root.path("reviewInvite").asBoolean(false);
                    } catch (Exception e) {
                        LOG.debug("No se pudo parsear metadata en EmailNotificationProvider: {}", e.getMessage());
                    }
                }

                if (bookingCode == null || bookingCode.isBlank()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(RES-\\d{4}-\\d+)").matcher(safeMessage + " " + safeSubject);
                    if (m.find()) {
                        bookingCode = m.group(1);
                    }
                }
                if (amount == null || amount.isBlank()) {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:pago(?:\\s+de)?|transacci[oó]n(?:\\s+de)?)\\s+([\\d.,]+)\\s*([A-Z]{3})?").matcher(safeMessage);
                    if (m.find()) {
                        amount = m.group(1);
                        if (m.group(2) != null && !m.group(2).isBlank()) {
                            currency = m.group(2);
                        }
                    }
                }
                if (currency == null || currency.isBlank()) {
                    currency = "COP";
                }
                if (paymentStatus == null) {
                    if (safeSubject.toLowerCase().contains("problema") || safeSubject.toLowerCase().contains("rechazado") || safeSubject.toLowerCase().contains("fallo") || safeSubject.toLowerCase().contains("fallido")) {
                        paymentStatus = "DECLINED";
                    } else if (!isPaymentReminder(safeSubject) && (safeSubject.toLowerCase().contains("aprobado") || safeSubject.toLowerCase().contains("comprobante") || safeSubject.toLowerCase().contains("exitoso") || safeSubject.toLowerCase().contains("confirmado"))) {
                        paymentStatus = "APPROVED";
                    }
                }

                String base = (frontendUrl != null ? frontendUrl : "http://localhost:5173").replaceAll("/+$", "");
                if (actionUrl == null && isPaymentReminder(safeSubject)) {
                    actionUrl = base + "/reservas";
                    actionLabel = texts.get(locale, "notif.payAction");
                } else if (actionUrl == null && paymentStatus != null) {
                    boolean isApproved = !"DECLINED".equalsIgnoreCase(paymentStatus);
                    if (isApproved) {
                        actionUrl = base + "/reservas";
                        actionLabel = texts.actionLabel(locale, "Continuar y Firmar Contrato");
                    } else {
                        actionUrl = base + "/reservas";
                        actionLabel = texts.actionLabel(locale, "Reintentar Pago en Mis Reservas");
                    }
                }

                String receiptCardHtml = "";
                if (paymentStatus != null) {
                    boolean isApproved = !"DECLINED".equalsIgnoreCase(paymentStatus);
                    String badgeBg = isApproved ? "#ecfdf5" : "#fef2f2";
                    String badgeBorder = isApproved ? "#10b981" : "#ef4444";
                    String badgeColor = isApproved ? "#047857" : "#b91c1c";
                    String badgeText = texts.get(locale, isApproved ? "notif.approvedBadge" : "notif.declinedBadge");
                    String statusText = texts.get(locale, isApproved ? "notif.approvedStatus" : "notif.declinedStatus");
                    String statusColor = isApproved ? "#047857" : "#dc2626";

                    String formattedAmount = amount != null ? amount : "";
                    try {
                        if (amount != null && !amount.isBlank()) {
                            double val = Double.parseDouble(amount.replace(",", ""));
                            formattedAmount = String.format("%,.0f", val).replace(',', '.');
                        }
                    } catch (Exception ignored) {}

                    String curr = currency != null ? currency : "COP";
                    String fechaStr = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(java.time.ZoneId.of("America/Bogota")).format(java.time.Instant.now());

                    String explNote = texts.get(locale, isApproved ? "notif.approvedNote" : "notif.declinedNote");

                    receiptCardHtml = String.format("""
                        <div style="margin: 20px 0;">
                          <div style="display: inline-block; background-color: %s; border: 1px solid %s; color: %s; font-size: 12px; font-weight: 800; padding: 6px 14px; border-radius: 9999px; letter-spacing: 0.5px;">
                            %s
                          </div>
                          <div style="background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 12px; padding: 18px 20px; margin-top: 14px;">
                            <div style="border-bottom: 1px dashed #cbd5e1; padding-bottom: 12px; margin-bottom: 12px;">
                              <span style="font-size: 11px; color: #64748b; text-transform: uppercase; font-weight: 600; letter-spacing: 0.5px; display: block;">%s</span>
                              <span style="font-size: 24px; font-weight: 800; color: #0f172a; margin-top: 2px; display: block;">$ %s %s</span>
                            </div>
                            <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                              %s
                              <tr>
                                <td style="color: #64748b; padding: 5px 0; font-weight: 500;">%s</td>
                                <td style="color: %s; padding: 5px 0; font-weight: 800; text-align: right;">%s</td>
                              </tr>
                              <tr>
                                <td style="color: #64748b; padding: 5px 0; font-weight: 500;">%s</td>
                                <td style="color: #0f172a; padding: 5px 0; font-weight: 600; text-align: right;">%s</td>
                              </tr>
                              <tr>
                                <td style="color: #64748b; padding: 5px 0; font-weight: 500;">%s</td>
                                <td style="color: #0f172a; padding: 5px 0; font-weight: 500; text-align: right;">%s</td>
                              </tr>
                              <tr>
                                <td style="color: #64748b; padding: 5px 0; font-weight: 500;">%s</td>
                                <td style="color: #0f172a; padding: 5px 0; font-weight: 500; text-align: right;">%s</td>
                              </tr>
                            </table>
                            <div style="margin-top: 12px; padding-top: 10px; border-top: 1px dashed #cbd5e1; font-size: 12px; color: #475569; line-height: 1.5;">
                              %s
                            </div>
                          </div>
                        </div>
                        """,
                        badgeBg, badgeBorder, badgeColor, badgeText,
                        texts.get(locale, "notif.amountLabel"),
                        formattedAmount, curr,
                        bookingCode != null && !bookingCode.isBlank() ? "<tr><td style=\"color: #64748b; padding: 5px 0; font-weight: 500;\">" + texts.get(locale, "notif.bookingLabel") + "</td><td style=\"color: #0f172a; padding: 5px 0; font-weight: 700; text-align: right;\">" + bookingCode + "</td></tr>" : "",
                        texts.get(locale, "notif.statusLabel"),
                        statusColor, statusText,
                        texts.get(locale, "notif.gatewayLabel"),
                        ("EFECTIVO".equalsIgnoreCase(paymentMethod) || (safeMessage + " " + safeSubject).toLowerCase().contains("efectivo")) ? texts.get(locale, "notif.cash") : texts.get(locale, "notif.wompi"),
                        texts.get(locale, "notif.dateLabel"),
                        fechaStr,
                        texts.get(locale, "notif.merchantLabel"),
                        texts.get(locale, "notif.merchantValue"),
                        explNote
                    );
                }

                if (reviewInvite) {
                    receiptCardHtml = receiptCardHtml + """
                        <div style="margin: 18px 0 4px; padding: 20px; text-align: center; background-color: #fffbeb; border: 1px solid #fde68a; border-radius: 14px;">
                          <div style="font-size: 34px; letter-spacing: 6px; color: #f59e0b; line-height: 1;">&#9733;&#9733;&#9733;&#9733;&#9733;</div>
                          <p style="margin: 10px 0 0; font-size: 15px; font-weight: 800; color: #92400e;">{{REVIEW_TITLE}}</p>
                          <p style="margin: 6px 0 0; font-size: 13px; color: #a16207; line-height: 1.5;">{{REVIEW_TEXT}}</p>
                        </div>
                        """.replace("{{REVIEW_TITLE}}", texts.get(locale, "notif.reviewTitle"))
                           .replace("{{REVIEW_TEXT}}", texts.get(locale, "notif.reviewText"));
                }

                String actionButtonHtml = "";
                String textMessage = safeMessage;
                if (actionUrl != null && !actionUrl.isBlank()) {
                    String label = (actionLabel != null && !actionLabel.isBlank()) ? actionLabel : texts.get(locale, "common.continue");
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
                    <html lang="{{LANG}}">
                    <head><meta charset="UTF-8"></head>
                    <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                      <div style="max-width: 540px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                        <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;">
                          <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                          <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">{{TAGLINE}}</p>
                        </div>
                        <div style="padding: 28px 30px 24px;">
                          <h2 style="color: #0f172a; margin: 0 0 14px; font-size: 17px; font-weight: 700;">{{SUBJECT}}</h2>
                          <div style="color: #334155; font-size: 14px; line-height: 1.6; margin: 0 0 10px; white-space: pre-line;">
                            {{MESSAGE}}
                          </div>
                          {{RECEIPT_CARD}}
                          {{ACTION_BUTTON}}
                          <div style="border-top: 1px solid #e2e8f0; padding-top: 18px; margin-top: 20px;">
                            <p style="color: #94a3b8; font-size: 12px; margin: 0;">
                              {{FOOTER}}
                            </p>
                          </div>
                        </div>
                      </div>
                    </body>
                    </html>
                    """
                        .replace("{{LANG}}", locale.toLanguageTag())
                        .replace("{{TAGLINE}}", texts.get(locale, "common.tagline"))
                        .replace("{{FOOTER}}", texts.get(locale, "common.autoNotice"))
                        .replace("{{SUBJECT}}", safeSubject)
                        .replace("{{MESSAGE}}", safeMessage)
                        .replace("{{RECEIPT_CARD}}", receiptCardHtml)
                        .replace("{{ACTION_BUTTON}}", actionButtonHtml);

                helper.setText(textMessage, brand(html));
                mailSender.send(mimeMessage);

                String messageId = "mail-" + UUID.randomUUID();
                LOG.info("Email entregado exitosamente vía JavaMailSender a '{}' con id '{}'", recipient, messageId);
                return ProviderDeliveryResult.success(messageId);
            } catch (Exception e) {
                LOG.error("Fallo al entregar correo a '{}': {}", recipient, e.getMessage());
                return ProviderDeliveryResult.failure("SMTP_DELIVERY_ERROR", e.getMessage());
            }
        }

        // Sin servidor de correo configurado el envío falla de forma explícita: nunca se simula una entrega
        LOG.error("No hay servidor de correo configurado; no se envió el mensaje a '{}'", recipient);
        return ProviderDeliveryResult.failure("EMAIL_NOT_CONFIGURED", "El servidor de correo no está configurado.");
    }

    private static boolean isPaymentReminder(String subject) {
        String lower = subject == null ? "" : subject.toLowerCase();
        return lower.contains("vencer") || lower.contains("plazo") || lower.contains("recordatorio");
    }
}
