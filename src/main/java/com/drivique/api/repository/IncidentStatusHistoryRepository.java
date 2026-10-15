package com.drivique.api.repository;

import com.drivique.api.model.IncidentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentStatusHistoryRepository extends JpaRepository<IncidentStatusHistory, UUID> {
    List<IncidentStatusHistory> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    void deleteByIncidentId(UUID incidentId);
}
