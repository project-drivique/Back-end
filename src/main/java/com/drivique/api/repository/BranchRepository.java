package com.drivique.api.repository;
import com.drivique.api.model.Branch;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByActiveTrueOrderByNameAsc();
    List<Branch> findByCityIdAndActiveTrueOrderByNameAsc(UUID cityId);
    Optional<Branch> findByIdAndActiveTrue(UUID id);
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
