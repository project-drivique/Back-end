package com.drivique.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Respuesta con los datos de un punto de entrega o devolución de reserva")
@JsonInclude(JsonInclude.Include.NON_NULL)
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
        Instant updatedAt,
        UUID driverId,
        String driverName,
        String driverPhone,
        String deliveryStatus,
        String deliveryPin
) {
    /** Forma anterior (sin datos de logística del conductor). */
    public DeliveryPointResponseDTO(UUID id, UUID reservationId, String pointType, String modality, UUID branchId,
                                    String branchName, UUID cityId, String cityName, String neighborhood, String address,
                                    String flightOrBusNumber, String referenceDetails, Instant createdAt, Instant updatedAt) {
        this(id, reservationId, pointType, modality, branchId, branchName, cityId, cityName, neighborhood, address,
                flightOrBusNumber, referenceDetails, createdAt, updatedAt, null, null, null, "PENDING", null);
    }

    /** El PIN de entrega solo lo ve el cliente de la reserva. */
    public static DeliveryPointResponseDTO from(com.drivique.api.model.ReservationDeliveryPoint pt, boolean includePin) {
        var driver = pt.getDriver();
        return new DeliveryPointResponseDTO(pt.getId(), pt.getReservation().getId(), pt.getPointType(), pt.getModality(),
                pt.getBranch() != null ? pt.getBranch().getId() : null, pt.getBranch() != null ? pt.getBranch().getName() : null,
                pt.getCity() != null ? pt.getCity().getId() : null, pt.getCity() != null ? pt.getCity().getName() : null,
                pt.getNeighborhood(), pt.getAddress(), pt.getFlightOrBusNumber(), pt.getReferenceDetails(),
                pt.getCreatedAt(), pt.getUpdatedAt(), driver == null ? null : driver.getId(),
                driver == null ? null : driver.getFullName(), driver == null ? null : driver.getPhone(),
                pt.getDeliveryStatus(), includePin ? pt.getDeliveryPin() : null);
    }
}
