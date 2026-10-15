package com.drivique.api.repository;

import com.drivique.api.model.SecurityConfigurationChange;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityConfigurationChangeRepository extends JpaRepository<SecurityConfigurationChange, UUID> {
    Optional<SecurityConfigurationChange> findFirstByConfigKeyOrderByChangedAtDesc(String configKey);
}
