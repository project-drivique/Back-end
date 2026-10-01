package com.drivique.api.dto;

import java.util.UUID;

public record CityResponseDTO(
        UUID id,
        UUID departmentId,
        String departmentName,
        String name,
        boolean hasAirport,
        boolean hasTerminal,
        boolean isActive) {
}
