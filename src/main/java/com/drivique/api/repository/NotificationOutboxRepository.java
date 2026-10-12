package com.drivique.api.repository;

import com.drivique.api.model.NotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    @Query("SELECT o FROM NotificationOutbox o WHERE (o.status = 'PENDING' OR o.status = 'FAILED') " +
           "AND o.scheduledAt <= :now AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= :now) " +
           "AND o.retryCount < o.maxRetries ORDER BY o.createdAt ASC")
    List<NotificationOutbox> findPendingOutboxItems(@Param("now") Instant now);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<NotificationOutbox> findByIdempotencyKey(String idempotencyKey);

    List<NotificationOutbox> findByNotificationId(UUID notificationId);
}
