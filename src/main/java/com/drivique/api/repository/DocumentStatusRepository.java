package com.drivique.api.repository;

import com.drivique.api.model.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentStatusRepository extends JpaRepository<DocumentStatus, UUID> {
    Optional<DocumentStatus> findByCode(String code);
}
