package com.drivique.api.repository;

import com.drivique.api.model.InspectionChecklistItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionChecklistItemRepository extends JpaRepository<InspectionChecklistItem, UUID> {}
