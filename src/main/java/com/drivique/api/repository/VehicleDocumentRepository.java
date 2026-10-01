package com.drivique.api.repository;

import com.drivique.api.model.VehicleDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {
    List<VehicleDocument> findByVehicleIdAndActiveTrueOrderByExpiresAtAsc(UUID vehicleId);
    List<VehicleDocument> findByVehicleIdOrderByCreatedAtDesc(UUID vehicleId);
    Optional<VehicleDocument> findByVehicleIdAndDocumentTypeAndActiveTrue(UUID vehicleId, String documentType);

    @Query("SELECT d FROM VehicleDocument d WHERE d.active = true AND d.expiresAt IS NOT NULL AND d.expiresAt <= :threshold ORDER BY d.expiresAt ASC")
    List<VehicleDocument> findExpiringDocuments(@Param("threshold") LocalDate threshold);

    @Query("SELECT d FROM VehicleDocument d WHERE d.vehicle.id = :vehicleId AND d.active = true AND d.expiresAt IS NOT NULL AND d.expiresAt <= :threshold ORDER BY d.expiresAt ASC")
    List<VehicleDocument> findExpiringDocumentsByVehicle(@Param("vehicleId") UUID vehicleId, @Param("threshold") LocalDate threshold);
}
