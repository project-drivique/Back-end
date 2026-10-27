package com.drivique.api.service;

import com.drivique.api.model.RentalContract;
import com.drivique.api.model.User;
import com.drivique.api.service.email.EmailLanguage;
import com.drivique.api.service.email.EmailTexts;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Envía al cliente su contrato firmado en PDF, protegido con su número de documento. */
@Service
public class ContractEmailService {
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


    private static final Logger LOG = LoggerFactory.getLogger(ContractEmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String fromAddress;
    private final String frontendUrl;

    public ContractEmailService(ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.auth.mail-from:no-reply@drivique.com}") String fromAddress,
            @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.frontendUrl = frontendUrl;
    }

    /** Datos mínimos para enviar el correo; se calculan dentro de la transacción para no depender de la sesión. */
    public record Delivery(String email, String firstName, String contractNumber, String reservationCode, String documentNumber, byte[] pdf) {}

    public static Delivery delivery(User customer, RentalContract contract, byte[] pdf) {
        return new Delivery(customer.getEmail(), customer.getFirstName(), contract.getContractNumber(),
                contract.getReservation() != null ? contract.getReservation().getCode() : "", customer.getDocumentNumber(), pdf);
    }

    public boolean sendSignedContract(Delivery delivery) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || delivery == null || delivery.email() == null || delivery.pdf() == null) {
            return false;
        }
        String password = passwordFor(delivery.documentNumber());
        if (password.isEmpty()) {
            // Sin número de documento registrado no se puede proteger el PDF, y un contrato nunca se envía sin clave
            LOG.warn("Contrato {} no enviado: el cliente no tiene documento registrado para protegerlo", delivery.contractNumber());
            return false;
        }
        try {
            byte[] protectedPdf = protect(delivery.pdf(), password);
            EmailTexts texts = emailTexts != null ? emailTexts : DEFAULT_TEXTS;
            Locale locale = emailLanguage != null ? emailLanguage.forEmail(delivery.email()) : EmailLanguage.DEFAULT;
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, texts.brandName()));
            helper.setTo(delivery.email());
            String number = delivery.contractNumber();
            String reservationCode = delivery.reservationCode();
            helper.setSubject(texts.get(locale, "contract.subject", "number", number));
            String base = frontendUrl.replaceAll("/+$", "");
            String html = """
                    <!DOCTYPE html>
                    <html lang="{{LANG}}"><head><meta charset="UTF-8"></head>
                    <body style="font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                      <div style="max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden;">
                        <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;">
                          <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                          <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">{{TAGLINE}}</p>
                        </div>
                        <div style="padding: 28px 30px 24px;">
                          <h2 style="color: #0f172a; margin: 0 0 14px; font-size: 17px;">{{TITLE}}</h2>
                          <p style="color: #334155; font-size: 14px; line-height: 1.6;">{{BODY}}</p>
                          <div style="text-align: center; margin: 26px 0 20px;">
                            <a href="{{BASE}}/reservas" style="display: inline-block; background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); color: #ffffff; text-decoration: none; font-size: 14px; font-weight: 700; padding: 13px 26px; border-radius: 10px;">{{BUTTON}}</a>
                          </div>
                          <p style="color: #94a3b8; font-size: 12px; margin: 0;">{{NOTICE}}</p>
                        </div>
                      </div>
                    </body></html>
                    """.replace("{{LANG}}", locale.toLanguageTag())
                    .replace("{{TAGLINE}}", texts.get(locale, "common.tagline"))
                    .replace("{{TITLE}}", texts.get(locale, "contract.title"))
                    .replace("{{BODY}}", texts.get(locale, "contract.body", "name", escape(delivery.firstName()), "number", escape(number), "reservation", escape(reservationCode)))
                    .replace("{{BUTTON}}", texts.get(locale, "contract.button"))
                    .replace("{{NOTICE}}", texts.get(locale, "common.autoNoticeShort"))
                    .replace("{{BASE}}", base);
            helper.setText(texts.get(locale, "contract.text", "name", delivery.firstName(), "number", number, "reservation", reservationCode), brand(html));
            helper.addAttachment(number + ".pdf", new org.springframework.core.io.ByteArrayResource(protectedPdf), "application/pdf");
            sender.send(message);
            LOG.info("Contrato {} enviado por correo a {}", number, delivery.email());
            return true;
        } catch (Exception e) {
            LOG.error("No se pudo enviar el contrato por correo a {}: {}", delivery.email(), e.getMessage());
            return false;
        }
    }

    /** Clave del PDF: el número de documento en mayúsculas y sin separadores (misma normalización que al abrir el contrato en la web y la App). */
    static String passwordFor(String documentNumber) {
        return documentNumber == null ? "" : documentNumber.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /** Cifra el PDF: se abre solo con la clave; el propietario es aleatorio para que no se pueda quitar la protección. */
    static byte[] protect(byte[] pdf, String userPassword) throws java.io.IOException, com.lowagie.text.DocumentException {
        com.lowagie.text.pdf.PdfReader reader = new com.lowagie.text.pdf.PdfReader(pdf);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        com.lowagie.text.pdf.PdfStamper stamper = new com.lowagie.text.pdf.PdfStamper(reader, out);
        stamper.setEncryption(userPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.util.UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                com.lowagie.text.pdf.PdfWriter.ALLOW_PRINTING, com.lowagie.text.pdf.PdfWriter.ENCRYPTION_AES_128);
        stamper.close();
        reader.close();
        return out.toByteArray();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
