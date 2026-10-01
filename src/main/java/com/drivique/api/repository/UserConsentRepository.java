package com.drivique.api.repository;

import com.drivique.api.model.UserConsent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    List<UserConsent> findByUserIdOrderByAcceptedAtDesc(UUID userId);
    boolean existsByUserIdAndConsentTypeAndDocumentVersion(UUID userId, String consentType, String documentVersion);
}
