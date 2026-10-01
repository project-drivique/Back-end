package com.drivique.api.repository;

import com.drivique.api.model.UserFavoriteVehicle;
import com.drivique.api.model.UserFavoriteVehicleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserFavoriteVehicleRepository extends JpaRepository<UserFavoriteVehicle, UserFavoriteVehicleId> {

    @Query("SELECT f FROM UserFavoriteVehicle f JOIN FETCH f.vehicle v WHERE f.user.id = :userId ORDER BY f.createdAt DESC")
    List<UserFavoriteVehicle> findByUserIdWithVehicle(@Param("userId") UUID userId);

    boolean existsByUserIdAndVehicleId(UUID userId, UUID vehicleId);

    void deleteByUserIdAndVehicleId(UUID userId, UUID vehicleId);
}
