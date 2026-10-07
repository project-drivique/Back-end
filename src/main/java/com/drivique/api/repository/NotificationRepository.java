package com.drivique.api.repository;

import com.drivique.api.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(UUID userId, boolean isRead);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndIsReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now, n.updatedAt = :now WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
