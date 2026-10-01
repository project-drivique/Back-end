package com.drivique.api.repository;
import com.drivique.api.model.TransmissionType; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface TransmissionTypeRepository extends JpaRepository<TransmissionType,UUID>{ List<TransmissionType> findByActiveTrueOrderByCodeAsc(); }
