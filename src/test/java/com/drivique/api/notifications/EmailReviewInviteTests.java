package com.drivique.api.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.drivique.api.service.notification.EmailNotificationProvider;
import com.drivique.api.service.notification.ProviderDeliveryResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

class EmailReviewInviteTests {

    @SuppressWarnings("unchecked")
    private String sentEmail(String metadata) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);

        ProviderDeliveryResult result = new EmailNotificationProvider(provider, "no-reply@drivique.com", "http://localhost:5173")
                .deliver("cliente@drivique.com", "¡Gracias por reservar con Drivique!", "Gracias por reservar, Danna.", metadata);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        captor.getValue().saveChanges();
        StringBuilder content = new StringBuilder();
        collect(captor.getValue().getContent(), content);
        return content.toString();
    }

    /** Recorre las partes del correo y junta su contenido ya decodificado. */
    private static void collect(Object part, StringBuilder out) throws Exception {
        if (part instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                collect(multipart.getBodyPart(i).getContent(), out);
            }
        } else if (part instanceof String text) {
            out.append(text).append(System.lineSeparator());
        }
    }

    @Test
    void reviewInviteEmailHasTheStarsBlockAndTheButtonToMyReservations() throws Exception {
        String raw = sentEmail("{\"actionPath\":\"/reservas?calificar=HLD-01546AAA-AC0F-4\",\"actionLabel\":\"Calificar y reseñar mi experiencia\",\"reviewInvite\":true}");

        assertThat(raw).contains("http://localhost:5173/reservas?calificar=HLD-01546AAA-AC0F-4");
        assertThat(raw).contains("Calificar y rese");
        assertThat(raw).contains("&#9733;&#9733;&#9733;&#9733;&#9733;");
        assertThat(raw).contains("¿Cómo estuvo tu viaje?");
    }

    @Test
    void emailsWithoutTheInviteFlagDoNotShowTheStarsBlock() throws Exception {
        String raw = sentEmail("{\"actionPath\":\"/reservas\",\"actionLabel\":\"Ver mis reservas\"}");

        assertThat(raw).doesNotContain("&#9733;");
    }
}
