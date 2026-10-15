package com.drivique.api.repository;

import com.drivique.api.model.DeliveryDriver;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryDriverRepository extends JpaRepository<DeliveryDriver, UUID> {
    List<DeliveryDriver> findByBranchIdOrderByFullNameAsc(UUID branchId);

    List<DeliveryDriver> findAllByOrderByFullNameAsc();

    boolean existsByBranchIdAndDocumentNumber(UUID branchId, String documentNumber);
}
