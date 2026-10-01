package com.drivique.api.repository;

import com.drivique.api.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    @Query("SELECT v FROM Vehicle v WHERE v.featured = true AND v.active = true AND v.status.allowsReservation = true AND v.status.active = true ORDER BY v.dailyRate ASC")
    List<Vehicle> findFeaturedVehicles();

    Optional<Vehicle> findByIdAndActiveTrue(UUID id);

    boolean existsByPlateIgnoreCase(String plate);
    boolean existsByVinIgnoreCase(String vin);
    boolean existsByPlateIgnoreCaseAndIdNot(String plate, UUID id);
    boolean existsByVinIgnoreCaseAndIdNot(String vin, UUID id);
}
