package com.drivique.api.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthEmailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public AuthEmailService(ObjectProvider<JavaMailSender> sender, @Value("${app.auth.mail-from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public void sendOtp(String email, String purpose, String code) {
        JavaMailSender mailSender = sender.getIfAvailable();
        if (mailSender == null) {
            System.err.println("El correo transaccional no está configurado. Simulando envío a " + email + " con código: " + code);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Código de seguridad Drivique");
        message.setText("Tu código para " + purpose + " es: " + code + ". Vence en 15 minutos.");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Error enviando email a " + email + " con código " + code + ". Ignorando en entorno local. Detalle: " + e.getMessage());
        }
    }
}