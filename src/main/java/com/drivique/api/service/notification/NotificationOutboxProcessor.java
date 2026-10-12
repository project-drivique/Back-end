package com.drivique.api.service.notification;

import com.drivique.api.model.Notification;
import com.drivique.api.model.NotificationDeliveryAttempt;
import com.drivique.api.model.NotificationOutbox;
import com.drivique.api.repository.NotificationDeliveryAttemptRepository;
import com.drivique.api.repository.NotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationOutboxProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final Map<String, NotificationChannelProvider> providers = new ConcurrentHashMap<>();

    public NotificationOutboxProcessor(
            NotificationOutboxRepository outboxRepository,
            NotificationDeliveryAttemptRepository attemptRepository,
            List<NotificationChannelProvider> channelProviders
    ) {
        this.outboxRepository = outboxRepository;
        this.attemptRepository = attemptRepository;
        for (NotificationChannelProvider p : channelProviders) {
            this.providers.put(p.getChannel().toUpperCase(), p);
        }
    }

    @Scheduled(fixedDelayString = "${drivique.notifications.outbox-poll-rate-ms:5000}")
    @Transactional
    public void processOutbox() {
        Instant now = Instant.now();
        List<NotificationOutbox> pendingItems = outboxRepository.findPendingOutboxItems(now);
        for (NotificationOutbox item : pendingItems) {
            try {
                deliverOutboxItem(item);
            } catch (Exception e) {
                LOG.error("Excepción inesperada al procesar outbox id '{}': {}", item.getId(), e.getMessage(), e);
            }
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOutboxIdAsync(UUID outboxId) {
        Optional<NotificationOutbox> itemOpt = outboxRepository.findById(outboxId);
        if (itemOpt.isPresent()) {
            NotificationOutbox item = itemOpt.get();
            if ("PENDING".equalsIgnoreCase(item.getStatus()) || "FAILED".equalsIgnoreCase(item.getStatus())) {
                deliverOutboxItem(item);
            }
        }
    }

    @Transactional
    public void deliverOutboxItem(NotificationOutbox item) {
        if ("SENT".equalsIgnoreCase(item.getStatus()) || "DEAD_LETTER".equalsIgnoreCase(item.getStatus())) {
            return;
        }

        Notification notification = item.getNotification();
        String channel = item.getChannel().toUpperCase();
        NotificationChannelProvider provider = providers.get(channel);

        if (provider == null) {
            LOG.warn("No hay proveedor disponible para el canal '{}'. Outbox id: {}", channel, item.getId());
            item.setStatus("FAILED");
            item.setRetryCount(item.getRetryCount() + 1);
            if (item.getRetryCount() >= item.getMaxRetries()) {
                item.setStatus("DEAD_LETTER");
            }
            outboxRepository.save(item);
            return;
        }

        item.setStatus("PROCESSING");
        outboxRepository.saveAndFlush(item);

        int currentAttemptNumber = item.getRetryCount() + 1;
        ProviderDeliveryResult result;
        try {
            result = provider.deliver(
                    item.getRecipient(),
                    notification != null ? notification.getSubject() : "",
                    notification != null ? notification.getMessage() : "",
                    item.getMetadata()
            );
        } catch (Exception ex) {
            LOG.error("Error al ejecutar entrega con proveedor '{}' para outbox id '{}': {}",
                    provider.getProviderName(), item.getId(), ex.getMessage());
            result = ProviderDeliveryResult.failure("PROVIDER_EXCEPTION", ex.getMessage());
        }

        if (result.success()) {
            item.setStatus("SENT");
            item.setProcessedAt(Instant.now());
            item.setNextRetryAt(null);
            outboxRepository.save(item);

            NotificationDeliveryAttempt attempt = new NotificationDeliveryAttempt(
                    item,
                    currentAttemptNumber,
                    provider.getProviderName(),
                    "SUCCESS",
                    result.providerMessageId(),
                    null,
                    null
            );
            attemptRepository.save(attempt);
            LOG.info("Outbox id '{}' enviado exitosamente vía '{}'", item.getId(), provider.getProviderName());
        } else {
            item.setRetryCount(currentAttemptNumber);
            if (item.getRetryCount() >= item.getMaxRetries()) {
                item.setStatus("DEAD_LETTER");
                item.setNextRetryAt(null);
                LOG.warn("Outbox id '{}' alcanzó el máximo de reintentos ({}) y pasó a DEAD_LETTER.",
                        item.getId(), item.getMaxRetries());
            } else {
                item.setStatus("FAILED");
                item.setNextRetryAt(Instant.now().plus(calculateBackoff(item.getRetryCount())));
                LOG.info("Outbox id '{}' falló en intento {}. Próximo reintento en: {}",
                        item.getId(), item.getRetryCount(), item.getNextRetryAt());
            }
            outboxRepository.save(item);

            NotificationDeliveryAttempt attempt = new NotificationDeliveryAttempt(
                    item,
                    currentAttemptNumber,
                    provider.getProviderName(),
                    "FAILED",
                    null,
                    result.errorCode(),
                    result.errorMessage()
            );
            attemptRepository.save(attempt);
        }
    }

    private Duration calculateBackoff(int retryCount) {
        return switch (retryCount) {
            case 1 -> Duration.ofSeconds(30);
            case 2 -> Duration.ofMinutes(2);
            default -> Duration.ofMinutes(10);
        };
    }
}
