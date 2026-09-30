package com.drivique.api.auth.repository;

import com.drivique.api.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);
    Optional<Role> findByCodeAndActiveTrue(String code);
}
