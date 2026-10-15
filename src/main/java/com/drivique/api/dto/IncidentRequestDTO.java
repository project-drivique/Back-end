package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** La prioridad no se envía: la calcula el servidor. */
public record IncidentRequestDTO(
        @NotBlank @Pattern(regexp = "RESERVATION|VEHICLE|PAYMENT|OTHER") String category,
        UUID vehicleId,
        UUID reservationId,
        UUID branchId,
        @NotBlank @Size(max = 150) String subject,
        @NotBlank @Size(max = 5000) String description) {
}
