package com.drivique.api.service;

import com.drivique.api.model.RentalContract;
import com.drivique.api.model.User;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Envía al cliente su contrato firmado en PDF. */
@Service
public class ContractEmailService {

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
    public record Delivery(String email, String firstName, String contractNumber, String reservationCode, byte[] pdf) {}

    public static Delivery delivery(User customer, RentalContract contract, byte[] pdf) {
        return new Delivery(customer.getEmail(), customer.getFirstName(), contract.getContractNumber(),
                contract.getReservation() != null ? contract.getReservation().getCode() : "", pdf);
    }

    public boolean sendSignedContract(Delivery delivery) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || delivery == null || delivery.email() == null || delivery.pdf() == null) {
            return false;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new InternetAddress(fromAddress, "Drivique"));
            helper.setTo(delivery.email());
            String number = delivery.contractNumber();
            String reservationCode = delivery.reservationCode();
            helper.setSubject("Tu contrato de alquiler " + number + " - Drivique");
            String base = frontendUrl.replaceAll("/+$", "");
            String html = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8"></head>
                    <body style="font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 24px;">
                      <div style="max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; overflow: hidden;">
                        <div style="background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); padding: 28px 32px; text-align: center;">
                          <h1 style="color: #ffffff; margin: 0; font-size: 24px; font-weight: 800; letter-spacing: 1px;">DRIVIQUE</h1>
                          <p style="color: rgba(255,255,255,0.85); margin: 6px 0 0; font-size: 13px;">Tu libertad sobre ruedas comienza aquí</p>
                        </div>
                        <div style="padding: 28px 30px 24px;">
                          <h2 style="color: #0f172a; margin: 0 0 14px; font-size: 17px;">Tu contrato quedó firmado</h2>
                          <p style="color: #334155; font-size: 14px; line-height: 1.6;">Hola {{NAME}}, adjuntamos en PDF el contrato <strong>{{NUMBER}}</strong> de tu reserva <strong>{{RESERVATION}}</strong>. Guárdalo: también puedes descargarlo cuando quieras desde tus reservas.</p>
                          <div style="text-align: center; margin: 26px 0 20px;">
                            <a href="{{BASE}}/reservas" style="display: inline-block; background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%); color: #ffffff; text-decoration: none; font-size: 14px; font-weight: 700; padding: 13px 26px; border-radius: 10px;">Ver mis reservas</a>
                          </div>
                          <p style="color: #94a3b8; font-size: 12px; margin: 0;">Este es un mensaje automático del sistema de notificaciones de Drivique.</p>
                        </div>
                      </div>
                    </body></html>
                    """.replace("{{NAME}}", escape(delivery.firstName())).replace("{{NUMBER}}", escape(number))
                    .replace("{{RESERVATION}}", escape(reservationCode)).replace("{{BASE}}", base);
            helper.setText("Hola " + delivery.firstName() + ", adjuntamos tu contrato " + number + " de la reserva " + reservationCode + ".", html);
            helper.addAttachment(number + ".pdf", new org.springframework.core.io.ByteArrayResource(delivery.pdf()), "application/pdf");
            sender.send(message);
            LOG.info("Contrato {} enviado por correo a {}", number, delivery.email());
            return true;
        } catch (Exception e) {
            LOG.error("No se pudo enviar el contrato por correo a {}: {}", delivery.email(), e.getMessage());
            return false;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
