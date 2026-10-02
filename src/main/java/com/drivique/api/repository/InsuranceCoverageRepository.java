package com.drivique.api.repository;
import com.drivique.api.model.InsuranceCoverage;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface InsuranceCoverageRepository extends JpaRepository<InsuranceCoverage, UUID> { List<InsuranceCoverage> findByActiveTrueOrderByNameAsc(); boolean existsByNameIgnoreCase(String name); boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id); }
