package com.drivique.api.repository;

import com.drivique.api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDocumentNumber(String documentNumber);
}
