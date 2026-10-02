package com.drivique.api.repository;
import com.drivique.api.model.MileagePlan;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MileagePlanRepository extends JpaRepository<MileagePlan, UUID> { List<MileagePlan> findByActiveTrueOrderByNameAsc(); boolean existsByNameIgnoreCase(String name); boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id); }
