package com.drivique.api.dto;

import java.util.UUID;

public record DepartmentResponseDTO(UUID id, String name, boolean isActive) {
}
