package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Respuesta con los datos de un punto de entrega o devolución de reserva")
public record DeliveryPointResponseDTO(
        UUID id,
        UUID reservationId,
        String pointType,
        String modality,
        UUID branchId,
        String branchName,
        UUID cityId,
        String cityName,
        String neighborhood,
        String address,
        String flightOrBusNumber,
        String referenceDetails,
        Instant createdAt,
        Instant updatedAt
) {}
