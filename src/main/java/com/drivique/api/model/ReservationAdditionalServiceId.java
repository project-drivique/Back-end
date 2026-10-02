package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationAdditionalServiceId implements Serializable {

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "additional_service_id")
    private UUID additionalServiceId;

    public ReservationAdditionalServiceId() {}

    public ReservationAdditionalServiceId(UUID reservationId, UUID additionalServiceId) {
        this.reservationId = reservationId;
        this.additionalServiceId = additionalServiceId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getAdditionalServiceId() { return additionalServiceId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationAdditionalServiceId that)) return false;
        return Objects.equals(reservationId, that.reservationId) && Objects.equals(additionalServiceId, that.additionalServiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, additionalServiceId);
    }
}
