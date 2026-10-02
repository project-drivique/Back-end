package com.drivique.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequestDTO(
        @NotNull(message = "El vehículo es obligatorio")
        UUID vehicleId,

        @NotNull(message = "La fecha de recogida es obligatoria")
        Instant pickupDate,

        @NotNull(message = "La fecha de devolución es obligatoria")
        Instant returnDate,

        @NotNull(message = "La cobertura de seguro es obligatoria")
        UUID insuranceCoverageId,

        @NotNull(message = "El plan de kilometraje es obligatorio")
        UUID mileagePlanId,

        List<UUID> additionalServiceIds,

        String couponCode,

        UUID cashPaymentBranchId
) {}
