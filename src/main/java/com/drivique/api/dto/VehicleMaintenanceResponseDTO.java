package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VehicleMaintenanceResponseDTO(
        UUID id, UUID vehicleId, String maintenanceTypeCode, String maintenanceTypeName,
        LocalDate scheduledDate, LocalDate completedDate, BigDecimal cost, String description
) {}
