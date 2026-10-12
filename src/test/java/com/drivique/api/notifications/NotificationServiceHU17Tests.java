package com.drivique.api.notifications;

import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.model.*;
import com.drivique.api.repository.NotificationOutboxRepository;
import com.drivique.api.repository.NotificationRepository;
import com.drivique.api.repository.NotificationTemplateRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.NotificationService;
import com.drivique.api.service.notification.NotificationOutboxProcessor;
import com.drivique.api.service.notification.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class NotificationServiceHU17Tests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private NotificationOutboxRepository outboxRepository;

    @Mock
    private NotificationTemplateRepository templateRepository;

    @Mock
    private NotificationTemplateService templateService;

    @Mock
    private NotificationOutboxProcessor outboxProcessor;

    private NotificationService notificationService;
    private User testUser;
    private UserPreference userPreference;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                userPreferenceRepository,
                mailSenderProvider,
                outboxRepository,
                templateRepository,
                templateService,
                outboxProcessor
        );

        testUser = new User("Mateo", "Lopez", "mateo@example.com", "hash123");
        testUser.setId(UUID.randomUUID());
        testUser.setPhone("+573001234567");

        userPreference = new UserPreference(testUser);
    }

    @Test
    void emitDomainEvent_WhenEmailDisabledInPreferences_SkipsEmailNotification() {
        userPreference.setEmailNotifications(false);
        userPreference.setPushNotifications(true);

        when(userPreferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(userPreference));

        NotificationTemplate emailTmpl = new NotificationTemplate(
                "PAY_EMAIL", "Pago", "PAYMENT", "EMAIL", "Asunto Pago", "Cuerpo", true
        );
        when(templateRepository.findByEventTypeAndIsActiveTrue("PAYMENT")).thenReturn(List.of(emailTmpl));

        notificationService.emitDomainEvent(testUser, "PAYMENT", "NORMAL", UUID.randomUUID(), Map.of(), "IDEMP-1");

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(outboxRepository, never()).save(any(NotificationOutbox.class));
    }

    @Test
    void emitDomainEvent_WhenCriticalEvent_BypassesUserOptOut() {
        userPreference.setEmailNotifications(false); // User turned off email

        when(userPreferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(userPreference));

        NotificationTemplate emailTmpl = new NotificationTemplate(
                "SEC_ALERT", "Alerta", "SECURITY", "EMAIL", "Alerta de seguridad", "Acceso detectado", true
        );
        when(templateRepository.findByEventTypeAndIsActiveTrue("SECURITY")).thenReturn(List.of(emailTmpl));
        when(templateService.render(any(), any())).thenAnswer(i -> i.getArgument(0));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        when(outboxRepository.save(any(NotificationOutbox.class))).thenAnswer(i -> i.getArgument(0));

        NotificationResponseDTO response = notificationService.emitDomainEvent(
                testUser, "SECURITY", "CRITICAL", UUID.randomUUID(), Map.of(), "CRIT-SEC-1"
        );

        assertThat(response).isNotNull();
        verify(notificationRepository).save(any(Notification.class));
        verify(outboxRepository).save(any(NotificationOutbox.class));
    }

    @Test
    void emitDomainEvent_IdempotencyPreventsDuplicateNotifications() {
        Notification existing = new Notification(
                testUser, "EMAIL", "PAYMENT", "Pago exitoso", "Recibo", null,
                "PAYMENT", "HIGH", "{}", "IDEMP-KEY-999"
        );

        when(notificationRepository.findByUserIdAndIdempotencyKey(testUser.getId(), "IDEMP-KEY-999"))
                .thenReturn(Optional.of(existing));

        NotificationResponseDTO response = notificationService.emitDomainEvent(
                testUser, "PAYMENT", "HIGH", UUID.randomUUID(), Map.of(), "IDEMP-KEY-999"
        );

        assertThat(response).isNotNull();
        assertThat(response.subject()).isEqualTo("Pago exitoso");
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(outboxRepository, never()).save(any(NotificationOutbox.class));
    }

    @Test
    void emitDomainEvent_PromotionRespectsMarketingPreference() {
        userPreference.setMarketingNotifications(false);
        when(userPreferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(userPreference));

        NotificationTemplate promoTmpl = new NotificationTemplate(
                "PROMO_EMAIL", "Promoción", "PROMOTION", "EMAIL", "Descuento", "Cuerpo promo", true
        );
        when(templateRepository.findByEventTypeAndIsActiveTrue("PROMOTION")).thenReturn(List.of(promoTmpl));

        notificationService.emitDomainEvent(testUser, "PROMOTION", "LOW", UUID.randomUUID(), Map.of(), "PROMO-1");

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void getUnreadCount_ReturnsAccurateCount() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("mateo@example.com"))
                .thenReturn(Optional.of(testUser));
        when(notificationRepository.countByUserIdAndIsReadFalse(testUser.getId())).thenReturn(7L);

        long count = notificationService.getUnreadCount("mateo@example.com");

        assertThat(count).isEqualTo(7L);
    }

    @Test
    void getNotificationById_ReturnsNotificationDetail() {
        UUID notifId = UUID.randomUUID();
        Notification notif = new Notification(
                testUser, "IN_APP", "GENERAL", "Aviso", "Mensaje", null,
                "REGISTRATION", "NORMAL", "{}", "IDEMP-22"
        );
        notif.setId(notifId);

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("mateo@example.com"))
                .thenReturn(Optional.of(testUser));
        when(notificationRepository.findByIdAndUserId(notifId, testUser.getId()))
                .thenReturn(Optional.of(notif));

        NotificationResponseDTO response = notificationService.getNotificationById(notifId, "mateo@example.com");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(notifId);
        assertThat(response.eventType()).isEqualTo("REGISTRATION");
    }
}
