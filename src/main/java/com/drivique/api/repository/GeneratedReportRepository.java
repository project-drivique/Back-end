package com.drivique.api.repository;

import com.drivique.api.model.GeneratedReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GeneratedReportRepository extends JpaRepository<GeneratedReport, UUID> {

    List<GeneratedReport> findAllByOrderByGeneratedAtDesc();

    List<GeneratedReport> findByReportTypeCodeIgnoreCaseOrderByGeneratedAtDesc(String reportTypeCode);

    List<GeneratedReport> findByGeneratedByIdOrderByGeneratedAtDesc(UUID generatedById);
}
