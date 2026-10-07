package com.drivique.api.repository;

import com.drivique.api.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByEntityNameIgnoreCaseOrderByCreatedAtDesc(String entityName);

    List<AuditLog> findByDomainNameIgnoreCaseOrderByCreatedAtDesc(String domainName);

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorUserId);

    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
}
