package com.drivique.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IncidentStatusRequestDTO(
        @NotBlank @Pattern(regexp = "OPEN|IN_REVIEW|RESOLVED|CLOSED") String status,
        @Size(max = 2000) String note) {
}
