package com.drivique.api.repository;

import com.drivique.api.model.UserConsent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    List<UserConsent> findByUserIdOrderByAcceptedAtDesc(UUID userId);
    boolean existsByUserIdAndConsentTypeAndDocumentVersion(UUID userId, String consentType, String documentVersion);

    @Modifying
    @Query("DELETE FROM UserConsent c WHERE c.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
