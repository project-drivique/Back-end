package com.drivique.api.repository;

import com.drivique.api.model.IncidentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentResponseRepository extends JpaRepository<IncidentResponse, UUID> {
    List<IncidentResponse> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
