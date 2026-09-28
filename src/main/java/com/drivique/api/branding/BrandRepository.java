package com.drivique.api.branding;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
public interface BrandRepository extends JpaRepository<BrandConfiguration, UUID> {
    Optional<BrandConfiguration> findByActiveTrue();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BrandConfiguration b where b.active = true")
    Optional<BrandConfiguration> findActiveForUpdate();
}
