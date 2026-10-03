package com.drivique.api.service;

import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.dto.SendNotificationRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Notification;
import com.drivique.api.model.User;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.NotificationRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.mailSenderProvider = mailSenderProvider;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDTO> getNotifications(String email, Boolean isRead) {
        User user = findUserByEmail(email);
        List<Notification> notifications;
        if (isRead != null) {
            notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(user.getId(), isRead);
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
        return notifications.stream().map(NotificationResponseDTO::fromEntity).toList();
    }

    @Transactional
    public NotificationResponseDTO markAsRead(UUID id, String email) {
        User user = findUserByEmail(email);
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada con id: " + id));
        notification.markAsRead();
        Notification saved = notificationRepository.save(notification);
        return NotificationResponseDTO.fromEntity(saved);
    }

    @Transactional
    public int markAllAsRead(String email) {
        User user = findUserByEmail(email);
        return notificationRepository.markAllAsReadByUserId(user.getId(), Instant.now());
    }

    @Transactional
    public NotificationResponseDTO sendNotification(SendNotificationRequestDTO request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id: " + request.userId()));
        return dispatchNotification(user, request.channel(), request.type(), request.subject(), request.message(), request.referenceId());
    }

    @Async
    public CompletableFuture<NotificationResponseDTO> sendNotificationAsync(SendNotificationRequestDTO request) {
        try {
            NotificationResponseDTO response = sendNotification(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            LOG.error("Error al enviar notificación asíncrona a usuario {}: {}", request.userId(), e.getMessage(), e);
            CompletableFuture<NotificationResponseDTO> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    @Async
    public CompletableFuture<NotificationResponseDTO> sendAsync(UUID userId, String channel, String type, String subject, String message, UUID referenceId) {
        return sendNotificationAsync(new SendNotificationRequestDTO(userId, channel, type, subject, message, referenceId));
    }

    @Transactional
    public NotificationResponseDTO send(User user, String channel, String type, String subject, String message, UUID referenceId) {
        return dispatchNotification(user, channel, type, subject, message, referenceId);
    }

    private NotificationResponseDTO dispatchNotification(User user, String channel, String type, String subject, String message, UUID referenceId) {
        String normalizedChannel = channel != null ? channel.trim().toUpperCase() : "IN_APP";
        String normalizedType = type != null ? type.trim().toUpperCase() : "GENERAL";

        switch (normalizedChannel) {
            case "EMAIL" -> sendEmailChannel(user, subject, message);
            case "SMS" -> sendSmsChannel(user, message);
            case "PUSH" -> sendPushChannel(user, subject, message);
            case "IN_APP" -> LOG.debug("Notificación In-App registrada para usuario: {}", user.getEmail());
            default -> LOG.warn("Canal no reconocido '{}', registrando como IN_APP", channel);
        }

        Notification notification = new Notification(
                user,
                normalizedChannel,
                normalizedType,
                subject,
                message,
                referenceId
        );
        Notification saved = notificationRepository.save(notification);
        return NotificationResponseDTO.fromEntity(saved);
    }

    private void sendEmailChannel(User user, String subject, String message) {
        Optional<UserPreference> pref = userPreferenceRepository.findByUserId(user.getId());
        boolean emailAllowed = pref.map(UserPreference::isEmailNotifications).orElse(true);

        if (!emailAllowed) {
            LOG.info("Notificación por correo omitida para '{}' debido a preferencias de usuario.", user.getEmail());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null && user.getEmail() != null) {
            try {
                SimpleMailMessage mailMessage = new SimpleMailMessage();
                mailMessage.setTo(user.getEmail());
                mailMessage.setSubject(subject);
                mailMessage.setText(message);
                mailSender.send(mailMessage);
                LOG.info("Correo electrónico enviado exitosamente a: {}", user.getEmail());
            } catch (Exception e) {
                LOG.error("Error al enviar correo electrónico a {}: {}", user.getEmail(), e.getMessage());
            }
        } else {
            LOG.info("JavaMailSender no disponible o correo vacío. Simulando envío de email a '{}': [{}] {}",
                    user.getEmail(), subject, message);
        }
    }

    private void sendSmsChannel(User user, String message) {
        Optional<UserPreference> pref = userPreferenceRepository.findByUserId(user.getId());
        boolean smsAllowed = pref.map(UserPreference::isSmsNotifications).orElse(true);

        if (!smsAllowed) {
            LOG.info("Notificación por SMS omitida para '{}' debido a preferencias de usuario.", user.getEmail());
            return;
        }

        String phone = user.getPhone();
        if (phone != null && !phone.isBlank()) {
            LOG.info("SMS enviado exitosamente al número '{}' del usuario '{}': {}", phone, user.getEmail(), message);
        } else {
            LOG.info("Usuario '{}' no tiene número telefónico configurado para SMS. Notificación registrada.", user.getEmail());
        }
    }

    private void sendPushChannel(User user, String subject, String message) {
        LOG.info("Push notification enviada para el usuario '{}': [{}] {}", user.getEmail(), subject, message);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con el correo: " + email));
    }
}
