package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_favorite_vehicles", schema = "fleet")
public class UserFavoriteVehicle {

    @EmbeddedId
    private UserFavoriteVehicleId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("vehicleId")
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected UserFavoriteVehicle() {}

    public UserFavoriteVehicle(User user, Vehicle vehicle) {
        this.user = user;
        this.vehicle = vehicle;
        this.id = new UserFavoriteVehicleId(user.getId(), vehicle.getId());
        this.createdAt = Instant.now();
    }

    public UserFavoriteVehicleId getId() { return id; }
    public User getUser() { return user; }
    public Vehicle getVehicle() { return vehicle; }
    public Instant getCreatedAt() { return createdAt; }
}
