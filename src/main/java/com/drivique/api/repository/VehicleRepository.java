package com.drivique.api.repository;

import com.drivique.api.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    @Query("SELECT v FROM Vehicle v WHERE v.featured = true AND v.active = true AND v.status.allowsReservation = true AND v.status.active = true ORDER BY v.dailyRate ASC")
    List<Vehicle> findFeaturedVehicles();

    Optional<Vehicle> findByIdAndActiveTrue(UUID id);

    List<Vehicle> findTop2ByCategoryIdAndActiveTrueOrderByModelAsc(UUID categoryId);

    List<Vehicle> findTop1ByActiveTrueOrderByModelAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id AND v.active = true")
    Optional<Vehicle> findByIdWithPessimisticLock(@Param("id") UUID id);

    boolean existsByCurrentBranchId(UUID branchId);
    long countByCurrentBranchId(UUID branchId);
    boolean existsByCurrentBranchIdAndCategoryIdAndActiveTrue(UUID branchId, UUID categoryId);
    boolean existsByPlateIgnoreCase(String plate);
    boolean existsByVinIgnoreCase(String vin);
    boolean existsByPlateIgnoreCaseAndIdNot(String plate, UUID id);
    boolean existsByVinIgnoreCaseAndIdNot(String vin, UUID id);
}

