package com.drivique.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BranchStaffResponseDTO(UUID userId, String fullName, String email, List<String> roleCodes, Instant assignedAt) {
}
