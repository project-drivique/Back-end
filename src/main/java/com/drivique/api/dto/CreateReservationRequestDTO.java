package com.drivique.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Solicitud para la creación de una nueva reserva")
public record CreateReservationRequestDTO(
        @NotNull(message = "El vehículo es obligatorio")
        @Schema(description = "ID del vehículo a reservar", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID vehicleId,

        @NotNull(message = "La fecha de recogida es obligatoria")
        @Schema(description = "Fecha y hora de recogida", example = "2026-10-15T10:00:00Z")
        Instant pickupDate,

        @NotNull(message = "La fecha de devolución es obligatoria")
        @Schema(description = "Fecha y hora de devolución", example = "2026-10-18T10:00:00Z")
        Instant returnDate,

        @NotNull(message = "La cobertura de seguro es obligatoria")
        @Schema(description = "ID de la cobertura de seguro seleccionada", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID insuranceCoverageId,

        @NotNull(message = "El plan de kilometraje es obligatorio")
        @Schema(description = "ID del plan de kilometraje seleccionado", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID mileagePlanId,

        @Schema(description = "Lista de IDs de servicios adicionales seleccionados")
        List<UUID> additionalServiceIds,

        @Schema(description = "Código de cupón o promoción opcional", example = "DESC10")
        String couponCode,

        @Schema(description = "ID de la sede donde se realizará el pago en efectivo (si aplica)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID cashPaymentBranchId,

        @Schema(description = "Puntos y modalidades de entrega/devolución opcionales configurados al crear la reserva")
        List<DeliveryPointRequestDTO> deliveryPoints
) {
    public CreateReservationRequestDTO(
            UUID vehicleId,
            Instant pickupDate,
            Instant returnDate,
            UUID insuranceCoverageId,
            UUID mileagePlanId,
            List<UUID> additionalServiceIds,
            String couponCode,
            UUID cashPaymentBranchId
    ) {
        this(vehicleId, pickupDate, returnDate, insuranceCoverageId, mileagePlanId, additionalServiceIds, couponCode, cashPaymentBranchId, null);
    }
}
