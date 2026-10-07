package com.drivique.api.repository;

import com.drivique.api.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
    Optional<UserPreference> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM UserPreference p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
