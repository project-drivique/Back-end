package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserFavoriteVehicleId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    public UserFavoriteVehicleId() {}

    public UserFavoriteVehicleId(UUID userId, UUID vehicleId) {
        this.userId = userId;
        this.vehicleId = vehicleId;
    }

    public UUID getUserId() { return userId; }
    public UUID getVehicleId() { return vehicleId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserFavoriteVehicleId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(vehicleId, that.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, vehicleId);
    }
}
