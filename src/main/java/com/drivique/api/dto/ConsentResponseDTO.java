package com.drivique.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentResponseDTO(UUID id, String consentType, String documentVersion, Instant acceptedAt, String ipAddress) {}
