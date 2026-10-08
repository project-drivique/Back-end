package com.drivique.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record ModifyReservationDatesRequestDTO(
        @NotNull(message = "La fecha de recogida es obligatoria")
        Instant pickupDate,
        @NotNull(message = "La fecha de devolución es obligatoria")
        Instant returnDate
) {}
