package com.drivique.api.repository;

import com.drivique.api.model.IncidentReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, UUID> {
    long countByIncidentCodeStartingWith(String prefix);
    List<IncidentReport> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);
    List<IncidentReport> findAllByOrderByCreatedAtDesc();
    List<IncidentReport> findByBranchIdOrderByCreatedAtDesc(UUID branchId);
}
