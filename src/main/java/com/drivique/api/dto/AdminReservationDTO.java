package com.drivique.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminReservationDTO(
        ReservationResponseDTO reservation,
        String customerPhone,
        String customerDocument,
        UUID branchId,
        String branchName,
        String cityName,
        String vehicleImageUrl,
        String paymentMethod,
        String paymentStatus,
        BigDecimal paidAmount,
        Instant paidAt,
        UUID contractId,
        String contractNumber,
        boolean contractSigned
) {}
