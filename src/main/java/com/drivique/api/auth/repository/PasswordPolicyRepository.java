package com.drivique.api.auth.repository;

import com.drivique.api.auth.entity.PasswordPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PasswordPolicyRepository extends JpaRepository<PasswordPolicy, UUID> {
    List<PasswordPolicy> findByActiveTrue();
}
