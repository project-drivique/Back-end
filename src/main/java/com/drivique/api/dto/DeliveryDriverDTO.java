package com.drivique.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DeliveryDriverDTO(
        UUID id,
        UUID branchId,
        String branchName,
        String fullName,
        String email,
        String phone,
        String documentType,
        String documentNumber,
        String licenseNumber,
        String licenseCategory,
        LocalDate licenseExpiresOn,
        String vehicleDescription,
        String status
) {}
