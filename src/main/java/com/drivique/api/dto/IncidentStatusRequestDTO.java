package com.drivique.api.dto;import jakarta.validation.constraints.*;public record IncidentStatusRequestDTO(@NotBlank @Pattern(regexp="OPEN|IN_REVIEW|RESOLVED|CLOSED") String status){}
