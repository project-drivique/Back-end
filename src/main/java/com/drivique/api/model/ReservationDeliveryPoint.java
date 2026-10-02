package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "reservation_delivery_points",
        schema = "rental",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_reservation_delivery_points_reservation_point_type", columnNames = {"reservation_id", "point_type"})
        }
)
public class ReservationDeliveryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "point_type", nullable = false, length = 10)
    private String pointType;

    @Column(name = "modality", nullable = false, length = 20)
    private String modality;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(length = 120)
    private String neighborhood;

    @Column(length = 255)
    private String address;

    @Column(name = "flight_or_bus_number", length = 60)
    private String flightOrBusNumber;

    @Column(name = "reference_details", length = 500)
    private String referenceDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ReservationDeliveryPoint() {}

    public ReservationDeliveryPoint(
            Reservation reservation,
            String pointType,
            String modality,
            Branch branch,
            City city,
            String neighborhood,
            String address,
            String flightOrBusNumber,
            String referenceDetails
    ) {
        this.reservation = reservation;
        this.pointType = pointType;
        this.modality = modality;
        this.branch = branch;
        this.city = city;
        this.neighborhood = neighborhood;
        this.address = address;
        this.flightOrBusNumber = flightOrBusNumber;
        this.referenceDetails = referenceDetails;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public String getPointType() { return pointType; }
    public String getModality() { return modality; }
    public Branch getBranch() { return branch; }
    public City getCity() { return city; }
    public String getNeighborhood() { return neighborhood; }
    public String getAddress() { return address; }
    public String getFlightOrBusNumber() { return flightOrBusNumber; }
    public String getReferenceDetails() { return referenceDetails; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setReservation(Reservation reservation) { this.reservation = reservation; }
    public void setPointType(String pointType) { this.pointType = pointType; }
    public void setModality(String modality) { this.modality = modality; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public void setCity(City city) { this.city = city; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public void setAddress(String address) { this.address = address; }
    public void setFlightOrBusNumber(String flightOrBusNumber) { this.flightOrBusNumber = flightOrBusNumber; }
    public void setReferenceDetails(String referenceDetails) { this.referenceDetails = referenceDetails; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
