package com.drivique.api.notifications;

import com.drivique.api.model.*;
import com.drivique.api.repository.NotificationDeliveryAttemptRepository;
import com.drivique.api.repository.NotificationOutboxRepository;
import com.drivique.api.service.notification.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NotificationOutboxProcessorTests {

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationDeliveryAttemptRepository attemptRepository;

    @Mock
    private NotificationChannelProvider emailProvider;

    private NotificationOutboxProcessor processor;
    private User testUser;
    private Notification testNotification;
    private NotificationOutbox testOutbox;

    @BeforeEach
    void setUp() {
        when(emailProvider.getChannel()).thenReturn("EMAIL");
        when(emailProvider.getProviderName()).thenReturn("JavaMailSender");

        processor = new NotificationOutboxProcessor(
                outboxRepository,
                attemptRepository,
                List.of(emailProvider)
        );

        testUser = new User("Laura", "Mendez", "laura@example.com", "hash");
        testUser.setId(UUID.randomUUID());

        testNotification = new Notification(
                testUser, "EMAIL", "GENERAL", "Asunto Prueba", "Cuerpo mensaje", null,
                "PAYMENT", "HIGH", "{}", "IDEMP-123"
        );

        testOutbox = new NotificationOutbox(
                testNotification, "EMAIL", "laura@example.com", "IDEMP-123-EMAIL", "{}"
        );
    }

    @Test
    void processOutbox_SuccessDelivery_MarksSentAndRecordsAttempt() {
        when(emailProvider.deliver(any(), any(), any(), any()))
                .thenReturn(ProviderDeliveryResult.success("msg-uuid-999"));

        processor.deliverOutboxItem(testOutbox);

        assertThat(testOutbox.getStatus()).isEqualTo("SENT");
        assertThat(testOutbox.getProcessedAt()).isNotNull();

        ArgumentCaptor<NotificationDeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        NotificationDeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo("SUCCESS");
        assertThat(attempt.getProvider()).isEqualTo("JavaMailSender");
        assertThat(attempt.getProviderMessageId()).isEqualTo("msg-uuid-999");
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
    }

    @Test
    void processOutbox_FailureDelivery_IncrementsRetryAndSchedulesNextRetry() {
        when(emailProvider.deliver(any(), any(), any(), any()))
                .thenReturn(ProviderDeliveryResult.failure("CONN_TIMEOUT", "Connection to mail server timed out"));

        processor.deliverOutboxItem(testOutbox);

        assertThat(testOutbox.getStatus()).isEqualTo("FAILED");
        assertThat(testOutbox.getRetryCount()).isEqualTo(1);
        assertThat(testOutbox.getNextRetryAt()).isNotNull();

        ArgumentCaptor<NotificationDeliveryAttempt> attemptCaptor = ArgumentCaptor.forClass(NotificationDeliveryAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        NotificationDeliveryAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo("FAILED");
        assertThat(attempt.getErrorCode()).isEqualTo("CONN_TIMEOUT");
        assertThat(attempt.getErrorMessage()).contains("Connection to mail server timed out");
    }

    @Test
    void processOutbox_MaxRetriesExceeded_TransitionsToDeadLetter() {
        testOutbox.setRetryCount(2); // Next fail will reach maxRetries (3)

        when(emailProvider.deliver(any(), any(), any(), any()))
                .thenReturn(ProviderDeliveryResult.failure("AUTH_FAILED", "Authentication failed token=xyz"));

        processor.deliverOutboxItem(testOutbox);

        assertThat(testOutbox.getStatus()).isEqualTo("DEAD_LETTER");
        assertThat(testOutbox.getRetryCount()).isEqualTo(3);
    }

    @Test
    void secretSanitizer_RedactsSensitiveData() {
        String sensitiveError = "Failed with Bearer eyJhbGciOiJIUzI1NiIsIn and password=SuperSecretPassword123";
        String sanitized = SecretSanitizer.sanitize(sensitiveError);

        assertThat(sanitized).doesNotContain("SuperSecretPassword123");
        assertThat(sanitized).contains("[REDACTED]");
    }
}
