package com.drivique.api.repository;
import com.drivique.api.model.FuelType; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface FuelTypeRepository extends JpaRepository<FuelType,UUID>{ List<FuelType> findByActiveTrueOrderByCodeAsc(); }
