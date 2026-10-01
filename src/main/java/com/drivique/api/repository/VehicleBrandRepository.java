package com.drivique.api.repository;
import com.drivique.api.model.VehicleBrand; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface VehicleBrandRepository extends JpaRepository<VehicleBrand,UUID>{ List<VehicleBrand> findByActiveTrueOrderByNameAsc(); boolean existsByNameIgnoreCase(String name); boolean existsByNameIgnoreCaseAndIdNot(String name,UUID id); }
