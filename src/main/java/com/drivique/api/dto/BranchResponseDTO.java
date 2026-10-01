package com.drivique.api.dto;
import java.time.LocalTime;
import java.util.UUID;
public record BranchResponseDTO(UUID id, String name, String address, UUID cityId, String cityName, String phone,
        LocalTime openingTime, LocalTime closingTime, boolean allowsCashPayment, boolean isActive) {}
