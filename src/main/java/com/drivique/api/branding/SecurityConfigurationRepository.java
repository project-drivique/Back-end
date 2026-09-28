package com.drivique.api.branding;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SecurityConfigurationRepository extends JpaRepository<SecurityConfiguration, UUID> {
    List<SecurityConfiguration> findByConfigKeyInOrderByConfigKeyAsc(Collection<String> keys);
}
