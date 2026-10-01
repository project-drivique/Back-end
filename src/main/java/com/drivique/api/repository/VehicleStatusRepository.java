package com.drivique.api.repository;
import com.drivique.api.model.VehicleStatus; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface VehicleStatusRepository extends JpaRepository<VehicleStatus,UUID>{ List<VehicleStatus> findByActiveTrueAndAllowsReservationTrueOrderByCodeAsc(); }
