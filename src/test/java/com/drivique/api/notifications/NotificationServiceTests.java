package com.drivique.api.notifications;

import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.dto.SendNotificationRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Notification;
import com.drivique.api.model.User;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.NotificationRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private NotificationService notificationService;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository,
                userRepository,
                userPreferenceRepository,
                mailSenderProvider
        );

        userId = UUID.randomUUID();
        testUser = new User("Laura", "Sanchez", "laura@example.com", "secret123");
        testUser.setId(userId);
        testUser.setPhone("+573001234567");
    }

    @Test
    void getNotifications_WhenIsReadIsNull_ReturnsAllUserNotifications() {
        Notification n1 = new Notification(testUser, "IN_APP", "RESERVATION", "Reserva confirmada", "Tu reserva está lista", UUID.randomUUID());
        Notification n2 = new Notification(testUser, "EMAIL", "SECURITY", "Alerta de seguridad", "Nuevo inicio de sesión", null);
        n2.markAsRead();

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@example.com")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(n2, n1));

        List<NotificationResponseDTO> result = notificationService.getNotifications("laura@example.com", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).subject()).isEqualTo("Alerta de seguridad");
        assertThat(result.get(1).subject()).isEqualTo("Reserva confirmada");
    }

    @Test
    void getNotifications_WhenIsReadFilterProvided_ReturnsFilteredNotifications() {
        Notification unread = new Notification(testUser, "IN_APP", "RESERVATION", "Reserva creada", "Pendiente de pago", UUID.randomUUID());

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@example.com")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false)).thenReturn(List.of(unread));

        List<NotificationResponseDTO> result = notificationService.getNotifications("laura@example.com", false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(0).subject()).isEqualTo("Reserva creada");
    }

    @Test
    void markAsRead_WhenNotificationExistsAndBelongsToUser_UpdatesReadStatus() {
        UUID notifId = UUID.randomUUID();
        Notification notification = new Notification(testUser, "IN_APP", "GENERAL", "Bienvenido", "Gracias por unirte", null);
        notification.setId(notifId);

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@example.com")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponseDTO result = notificationService.markAsRead(notifId, "laura@example.com");

        assertThat(result.isRead()).isTrue();
        assertThat(result.readAt()).isNotNull();
        assertThat(notification.isRead()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_WhenNotificationNotFound_ThrowsResourceNotFoundException() {
        UUID notifId = UUID.randomUUID();
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@example.com")).thenReturn(Optional.of(testUser));
        when(notificationRepository.findByIdAndUserId(notifId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(notifId, "laura@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notificación no encontrada");
    }

    @Test
    void markAllAsRead_WhenCalled_UpdatesAllUnreadForUser() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("laura@example.com")).thenReturn(Optional.of(testUser));
        when(notificationRepository.markAllAsReadByUserId(eq(userId), any(Instant.class))).thenReturn(5);

        int updatedCount = notificationService.markAllAsRead("laura@example.com");

        assertThat(updatedCount).isEqualTo(5);
        verify(notificationRepository).markAllAsReadByUserId(eq(userId), any(Instant.class));
    }

    @Test
    void sendNotification_InApp_PersistsNotification() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                userId,
                "IN_APP",
                "RESERVATION",
                "Reserva confirmada",
                "Tu reserva #DRV-1001 ha sido confirmada",
                UUID.randomUUID()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDTO result = notificationService.sendNotification(request);

        assertThat(result.channel()).isEqualTo("IN_APP");
        assertThat(result.type()).isEqualTo("RESERVATION");
        assertThat(result.subject()).isEqualTo("Reserva confirmada");
        assertThat(result.isRead()).isFalse();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getChannel()).isEqualTo("IN_APP");
        assertThat(captor.getValue().getUser().getId()).isEqualTo(userId);
    }

    @Test
    void sendNotification_Email_WhenPreferenceEnabledAndMailSenderAvailable_SendsMailAndPersists() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                userId,
                "EMAIL",
                "SECURITY",
                "Cambio de contraseña",
                "Tu contraseña fue actualizada",
                null
        );

        UserPreference preference = new UserPreference(testUser);
        preference.setEmailNotifications(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDTO result = notificationService.sendNotification(request);

        assertThat(result.channel()).isEqualTo("EMAIL");
        verify(javaMailSender).send(any(SimpleMailMessage.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendNotification_Email_WhenPreferenceDisabled_DoesNotSendMailButPersists() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                userId,
                "EMAIL",
                "PROMOTION",
                "Oferta 20% OFF",
                "Aprovecha este descuento",
                null
        );

        UserPreference preference = new UserPreference(testUser);
        preference.setEmailNotifications(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDTO result = notificationService.sendNotification(request);

        assertThat(result.channel()).isEqualTo("EMAIL");
        verify(javaMailSender, never()).send(any(SimpleMailMessage.class));
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendNotification_Sms_WhenPreferenceEnabled_LogsAndPersists() {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                userId,
                "SMS",
                "SECURITY",
                "Código OTP",
                "Tu código de verificación es 482910",
                null
        );

        UserPreference preference = new UserPreference(testUser);
        preference.setSmsNotifications(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDTO result = notificationService.sendNotification(request);

        assertThat(result.channel()).isEqualTo("SMS");
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendNotificationAsync_ExecutesAsynchronouslyAndReturnsFuture() throws Exception {
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                userId,
                "IN_APP",
                "GENERAL",
                "Notificación Asíncrona",
                "Prueba de envío asíncrono",
                null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        CompletableFuture<NotificationResponseDTO> future = notificationService.sendNotificationAsync(request);

        assertThat(future).isNotNull();
        NotificationResponseDTO response = future.get();
        assertThat(response.subject()).isEqualTo("Notificación Asíncrona");
    }

    @Test
    void sendNotification_WhenUserNotFound_ThrowsResourceNotFoundException() {
        UUID nonExistentUserId = UUID.randomUUID();
        SendNotificationRequestDTO request = new SendNotificationRequestDTO(
                nonExistentUserId,
                "IN_APP",
                "GENERAL",
                "Prueba",
                "Mensaje",
                null
        );

        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.sendNotification(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Usuario no encontrado");
    }
}
