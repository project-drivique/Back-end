package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(description = "Solicitud para registrar o actualizar múltiples puntos de entrega/devolución")
public record ConfigureDeliveryPointsRequestDTO(
        @NotEmpty(message = "Debe proporcionar al menos un punto de entrega/devolución")
        @Valid
        List<DeliveryPointRequestDTO> points
) {}
