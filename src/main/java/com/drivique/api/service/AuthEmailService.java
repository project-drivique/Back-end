package com.drivique.api.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class AuthEmailService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.drivique.api.service.BrandEmailTheme brandTheme;

    private String brand(String html) {
        return brandTheme == null ? html : brandTheme.apply(html);
    }

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public AuthEmailService(ObjectProvider<JavaMailSender> sender, @Value("${app.auth.mail-from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AuthEmailService.class);

    public void sendOtp(String email, String purpose, String code) {
        LOG.info("\n========================================================\n" +
                 "[DRIVIQUE SEGURIDAD] CÓDIGO OTP PARA: {}\n" +
                 "Propósito: {}\n" +
                 "CÓDIGO DE 6 DÍGITOS: {}\n" +
                 "========================================================", email, purpose, code);

        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            LOG.warn("El correo transaccional (SMTP) no está configurado en .env o application.yml. En producción configure SPRING_MAIL_HOST, SPRING_MAIL_USERNAME y SPRING_MAIL_PASSWORD.");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(new InternetAddress(from, "Drivique"));
            helper.setTo(email);
            helper.setSubject("Drivique - Tu código de seguridad: " + code);

            String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                  <div style="max-width: 520px; margin: 0 auto; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);">
                    <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;">
                      <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                      <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">Tu libertad sobre ruedas comienza aquí</p>
                    </div>
                    <div style="padding: 32px 32px 28px;">
                      <h2 style="color: #0f172a; margin: 0 0 12px; font-size: 18px; font-weight: 700;">Código de Verificación</h2>
                      <p style="color: #475569; font-size: 14px; line-height: 1.6; margin: 0 0 20px;">
                        Usa el siguiente código de 6 dígitos para <strong>{{PURPOSE}}</strong> en la plataforma Drivique:
                      </p>
                      <div style="background-color: #f1f5f9; border: 2px dashed #cbd5e1; border-radius: 12px; padding: 18px; text-align: center; margin: 0 0 20px;">
                        <span style="font-family: 'Courier New', Courier, monospace; font-size: 34px; font-weight: 800; letter-spacing: 8px; color: #1e3a8a;">{{CODE}}</span>
                      </div>
                      <p style="color: #64748b; font-size: 13px; line-height: 1.5; margin: 0 0 24px;">
                        ⏱️ Este código vence en <strong>15 minutos</strong>. Por seguridad, no lo compartas con nadie.
                      </p>
                      <div style="border-top: 1px solid #e2e8f0; padding-top: 20px;">
                        <p style="color: #94a3b8; font-size: 12px; margin: 0;">
                          Si no solicitaste este código, puedes ignorar este correo con total tranquilidad.
                        </p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.replace("{{PURPOSE}}", purpose).replace("{{CODE}}", code);

            String text = "Tu código de seguridad Drivique para " + purpose + " es: " + code + ". Vence en 15 minutos.";
            helper.setText(text, brand(html));

            mailSender.send(mimeMessage);
            LOG.info("Correo con código OTP enviado exitosamente a {}", email);
        } catch (Exception e) {
            LOG.error("No se pudo entregar el correo SMTP a {}: {}. Verifique las credenciales de correo de aplicación.", email, e.getMessage());
        }
    }
}