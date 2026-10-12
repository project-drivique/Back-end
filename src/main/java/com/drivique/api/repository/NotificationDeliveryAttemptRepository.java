package com.drivique.api.repository;

import com.drivique.api.model.NotificationDeliveryAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttempt, UUID> {

    List<NotificationDeliveryAttempt> findByOutboxIdOrderByAttemptNumberAsc(UUID outboxId);

    long countByOutboxId(UUID outboxId);
}
