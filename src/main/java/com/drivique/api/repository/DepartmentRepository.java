package com.drivique.api.repository;

import com.drivique.api.model.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByActiveTrueOrderByNameAsc();
    Optional<Department> findByIdAndActiveTrue(UUID id);
}
