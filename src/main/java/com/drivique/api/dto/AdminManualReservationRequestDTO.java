package com.drivique.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminManualReservationRequestDTO(
        @NotBlank @Email String customerEmail,
        @NotNull @Valid CreateReservationRequestDTO reservation
) {}
