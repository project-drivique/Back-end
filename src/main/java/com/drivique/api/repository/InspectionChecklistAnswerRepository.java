package com.drivique.api.repository;

import com.drivique.api.model.InspectionChecklistAnswer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionChecklistAnswerRepository extends JpaRepository<InspectionChecklistAnswer, UUID> {}
