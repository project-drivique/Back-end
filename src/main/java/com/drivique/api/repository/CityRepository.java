package com.drivique.api.repository;

import com.drivique.api.model.City;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, UUID> {
    List<City> findByActiveTrueOrderByNameAsc();
    List<City> findByDepartmentIdAndActiveTrueOrderByNameAsc(UUID departmentId);
    boolean existsByDepartmentIdAndNameIgnoreCase(UUID departmentId, String name);
    boolean existsByDepartmentIdAndNameIgnoreCaseAndIdNot(UUID departmentId, String name, UUID id);
}
