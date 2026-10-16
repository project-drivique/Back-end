package com.drivique.api.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Normaliza la contraseña del servidor SMTP (Gmail) eliminando espacios
 * en blanco accidentales que se copian del panel de Google.
 */
@Configuration
public class MailConfig implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof JavaMailSenderImpl mailSender) {
            String password = mailSender.getPassword();
            if (password != null && password.contains(" ")) {
                mailSender.setPassword(password.replace(" ", ""));
            }
        }
        return bean;
    }
}
