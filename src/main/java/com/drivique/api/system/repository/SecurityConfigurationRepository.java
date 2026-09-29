package com.drivique.api.system.repository;

import com.drivique.api.system.entity.SecurityConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SecurityConfigurationRepository extends JpaRepository<SecurityConfiguration, UUID> {
    List<SecurityConfiguration> findByConfigKeyInOrderByConfigKeyAsc(Collection<String> keys);
}
