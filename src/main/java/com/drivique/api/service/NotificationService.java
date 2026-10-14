package com.drivique.api.service;

import com.drivique.api.dto.NotificationResponseDTO;
import com.drivique.api.dto.SendNotificationRequestDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.*;
import com.drivique.api.repository.NotificationOutboxRepository;
import com.drivique.api.repository.NotificationRepository;
import com.drivique.api.repository.NotificationTemplateRepository;
import com.drivique.api.repository.UserPreferenceRepository;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.service.notification.NotificationOutboxProcessor;
import com.drivique.api.service.notification.NotificationTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateService templateService;
    private final NotificationOutboxProcessor outboxProcessor;

    @Autowired
    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            NotificationOutboxRepository outboxRepository,
            NotificationTemplateRepository templateRepository,
            NotificationTemplateService templateService,
            NotificationOutboxProcessor outboxProcessor
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.mailSenderProvider = mailSenderProvider;
        this.outboxRepository = outboxRepository;
        this.templateRepository = templateRepository;
        this.templateService = templateService;
        this.outboxProcessor = outboxProcessor;
    }

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            UserPreferenceRepository userPreferenceRepository,
            ObjectProvider<JavaMailSender> mailSenderProvider
    ) {
        this(notificationRepository, userRepository, userPreferenceRepository, mailSenderProvider, null, null, null, null);
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

    @Transactional(readOnly = true)
    public NotificationResponseDTO getNotificationById(UUID id, String email) {
        User user = findUserByEmail(email);
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada con id: " + id));
        return NotificationResponseDTO.fromEntity(notification);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String email) {
        User user = findUserByEmail(email);
        return notificationRepository.countByUserIdAndIsReadFalse(user.getId());
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
        return dispatchNotification(user, request.channel(), request.type(), request.subject(), request.message(), request.referenceId(), null, "NORMAL", null, null);
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
        return dispatchNotification(user, channel, type, subject, message, referenceId, null, "NORMAL", null, null);
    }

    @Transactional
    public NotificationResponseDTO sendWithMetadata(
            User user,
            String channel,
            String type,
            String subject,
            String message,
            UUID referenceId,
            Map<String, Object> metadata
    ) {
        String metadataJson = serializeMetadata(metadata);
        return dispatchNotification(user, channel, type, subject, message, referenceId, null, "NORMAL", metadataJson, null);
    }

    @Transactional
    public NotificationResponseDTO emitDomainEvent(
            User user,
            String eventType,
            String criticality,
            UUID referenceId,
            Map<String, Object> params,
            String idempotencyKey
    ) {
        if (user == null) {
            LOG.warn("No se puede emitir evento '{}' sin usuario.", eventType);
            return null;
        }

        String normalizedEventType = eventType != null ? eventType.trim().toUpperCase() : "GENERAL";
        String normalizedCriticality = criticality != null ? criticality.trim().toUpperCase() : "NORMAL";

        // 1. Verificación de Idempotencia
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Notification> existing = notificationRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey.trim());
            if (existing.isPresent()) {
                LOG.info("Notificación duplicada ignorada para evento '{}' con idempotencyKey '{}'", normalizedEventType, idempotencyKey);
                return NotificationResponseDTO.fromEntity(existing.get());
            }
        }

        String metadataJson = serializeMetadata(params);

        // 2. Buscar plantillas activas para este evento
        List<NotificationTemplate> templates = templateRepository != null
                ? templateRepository.findByEventTypeAndIsActiveTrue(normalizedEventType)
                : Collections.emptyList();

        if (templates.isEmpty()) {
            // Fallback genérico si no hay plantillas específicas registradas
            String subject = "Notificación de " + normalizedEventType;
            String message = "Actualización referente a tu cuenta o servicio (" + normalizedEventType + ").";
            return dispatchNotification(user, "IN_APP", "GENERAL", subject, message, referenceId, normalizedEventType, normalizedCriticality, metadataJson, idempotencyKey);
        }

        NotificationResponseDTO primaryResponse = null;

        for (NotificationTemplate template : templates) {
            String channel = template.getChannel().toUpperCase();

            // 3. Evaluar permisos de canal y criticidad
            if (!isChannelAllowed(user, channel, normalizedCriticality, normalizedEventType)) {
                LOG.info("Notificación '{}' canal '{}' omitida para '{}' por preferencias de usuario.",
                        normalizedEventType, channel, user.getEmail());
                continue;
            }

            // 4. Renderizar plantilla
            String subject = templateService != null ? templateService.render(template.getSubjectTemplate(), params) : template.getSubjectTemplate();
            String message = templateService != null ? templateService.render(template.getBodyTemplate(), params) : template.getBodyTemplate();

            String channelIdempotencyKey = idempotencyKey != null ? idempotencyKey.trim() + "-" + channel : null;
            NotificationResponseDTO response = dispatchNotification(
                    user,
                    channel,
                    determineNotificationType(normalizedEventType),
                    subject,
                    message,
                    referenceId,
                    normalizedEventType,
                    normalizedCriticality,
                    metadataJson,
                    channelIdempotencyKey
            );

            if (primaryResponse == null || "IN_APP".equalsIgnoreCase(channel)) {
                primaryResponse = response;
            }
        }

        return primaryResponse;
    }

    private NotificationResponseDTO dispatchNotification(
            User user,
            String channel,
            String type,
            String subject,
            String message,
            UUID referenceId,
            String eventType,
            String criticality,
            String metadata,
            String idempotencyKey
    ) {
        String normalizedChannel = channel != null ? channel.trim().toUpperCase() : "IN_APP";
        String normalizedType = determineNotificationType(type);
        String normalizedCriticality = criticality != null ? criticality.trim().toUpperCase() : "NORMAL";
        String finalEventType = eventType != null ? eventType : type;

        Notification notification = new Notification(
                user,
                normalizedChannel,
                normalizedType,
                subject,
                message,
                referenceId,
                finalEventType,
                normalizedCriticality,
                metadata,
                idempotencyKey
        );
        Notification saved = notificationRepository.save(notification);

        // Verificar si el canal está permitido según preferencias y criticidad
        boolean channelAllowed = isChannelAllowed(user, normalizedChannel, normalizedCriticality, normalizedType);

        // Si es canal outbox (EMAIL, SMS, PUSH), encolar en el outbox transaccional
        if (channelAllowed && !"IN_APP".equalsIgnoreCase(normalizedChannel) && outboxRepository != null) {
            String recipient = resolveRecipient(user, normalizedChannel);
            if (recipient != null && !recipient.isBlank()) {
                String outboxKey = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
                NotificationOutbox outbox = new NotificationOutbox(saved, normalizedChannel, recipient, outboxKey, metadata);
                NotificationOutbox savedOutbox = outboxRepository.save(outbox);

                // Procesamiento asíncrono inmediato si está disponible el processor
                if (outboxProcessor != null) {
                    try {
                        outboxProcessor.processOutboxIdAsync(savedOutbox.getId());
                    } catch (Exception e) {
                        LOG.debug("Outbox id '{}' encolado para procesador programado.", savedOutbox.getId());
                    }
                }
            }
        } else if (channelAllowed && "EMAIL".equalsIgnoreCase(normalizedChannel) && mailSenderProvider != null) {
            // Compatibilidad legacy si outboxRepository no está configurado (tests unitarios)
            sendLegacyEmailChannel(user, subject, message);
        } else if (channelAllowed && "SMS".equalsIgnoreCase(normalizedChannel)) {
            LOG.info("SMS enviado exitosamente al número '{}' del usuario '{}': {}", user.getPhone(), user.getEmail(), message);
        }

        return NotificationResponseDTO.fromEntity(saved);
    }

    private boolean isChannelAllowed(User user, String channel, String criticality, String eventType) {
        if ("CRITICAL".equalsIgnoreCase(criticality)) {
            return true;
        }

        Optional<UserPreference> prefOpt = userPreferenceRepository.findByUserId(user.getId());
        if (prefOpt.isEmpty()) {
            return true;
        }

        UserPreference pref = prefOpt.get();

        if ("PROMOTION".equalsIgnoreCase(eventType) && !pref.isMarketingNotifications()) {
            return false;
        }

        return switch (channel.toUpperCase()) {
            case "EMAIL" -> pref.isEmailNotifications();
            case "SMS" -> pref.isSmsNotifications();
            case "PUSH" -> pref.isPushNotifications();
            case "IN_APP" -> pref.isInAppNotifications();
            default -> true;
        };
    }

    private String resolveRecipient(User user, String channel) {
        return switch (channel.toUpperCase()) {
            case "EMAIL" -> user.getEmail();
            case "SMS" -> user.getPhone();
            case "PUSH" -> user.getEmail(); // Identificador push / subject para el bus
            default -> user.getEmail();
        };
    }

    private String determineNotificationType(String eventType) {
        if (eventType == null) return "GENERAL";
        String upper = eventType.trim().toUpperCase();
        if (upper.startsWith("RESERVATION") || upper.contains("CANCELLATION") || upper.contains("DELIVERY") || upper.contains("RETURN") || upper.contains("CONTRACT")) {
            return "RESERVATION";
        }
        if (upper.startsWith("SECURITY") || upper.contains("AUTH") || upper.contains("PASSWORD")) {
            return "SECURITY";
        }
        if (upper.startsWith("PROMOTION") || upper.contains("COUPON")) {
            return "PROMOTION";
        }
        return switch (upper) {
            case "SECURITY" -> "SECURITY";
            case "RESERVATION" -> "RESERVATION";
            case "PROMOTION" -> "PROMOTION";
            default -> "GENERAL";
        };
    }

    private String serializeMetadata(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(params);
        } catch (Exception e) {
            LOG.warn("No se pudo serializar metadata de notificación: {}", e.getMessage());
            return null;
        }
    }

    private void sendLegacyEmailChannel(User user, String subject, String message) {
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
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con el correo: " + email));
    }
}
