package com.drivique.api.repository;

import com.drivique.api.model.IncidentAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAttachmentRepository extends JpaRepository<IncidentAttachment, UUID> {
    List<IncidentAttachment> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    void deleteByIncidentId(UUID incidentId);
    long countByIncidentId(UUID incidentId);
}
