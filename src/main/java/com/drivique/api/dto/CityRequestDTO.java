package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CityRequestDTO(
        @NotNull UUID departmentId,
        @NotBlank @Size(max = 100) String name,
        boolean hasAirport,
        boolean hasTerminal) {
}
