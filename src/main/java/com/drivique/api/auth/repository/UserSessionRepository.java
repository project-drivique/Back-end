package com.drivique.api.auth.repository;

import com.drivique.api.auth.entity.User;
import com.drivique.api.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user = :user AND s.revokedAt IS NULL")
    void revokeAllActiveSessionsForUser(@Param("user") User user, @Param("now") Instant now);
}
