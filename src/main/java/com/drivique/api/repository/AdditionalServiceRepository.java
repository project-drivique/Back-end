package com.drivique.api.repository;
import com.drivique.api.model.AdditionalService;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AdditionalServiceRepository extends JpaRepository<AdditionalService, UUID> { List<AdditionalService> findByActiveTrueOrderByNameAsc(); boolean existsByNameIgnoreCase(String name); boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id); }
