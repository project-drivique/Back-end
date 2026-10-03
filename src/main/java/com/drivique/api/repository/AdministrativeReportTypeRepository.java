package com.drivique.api.repository;

import com.drivique.api.model.AdministrativeReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdministrativeReportTypeRepository extends JpaRepository<AdministrativeReportType, UUID> {

    Optional<AdministrativeReportType> findByCodeIgnoreCaseAndActiveTrue(String code);

    Optional<AdministrativeReportType> findByCodeIgnoreCase(String code);

    List<AdministrativeReportType> findByActiveTrueOrderByCodeAsc();
}
