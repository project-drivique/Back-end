package com.drivique.api.repository;

import com.drivique.api.model.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findByEventTypeAndIsActiveTrue(String eventType);

    Optional<NotificationTemplate> findByEventTypeAndChannelAndIsActiveTrue(String eventType, String channel);

    Optional<NotificationTemplate> findByCodeAndIsActiveTrue(String code);
}
