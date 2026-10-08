package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelReservationRequestDTO(
        @NotBlank(message = "El motivo de cancelación es obligatorio")
        @Size(max = 255, message = "El motivo no puede exceder los 255 caracteres")
        String reason
) {}
