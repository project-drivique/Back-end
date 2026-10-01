package com.drivique.api.repository;

import com.drivique.api.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    Optional<UserPreference> findByUserId(UUID userId);
}
