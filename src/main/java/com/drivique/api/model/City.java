package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cities", schema = "location")
public class City {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "has_airport", nullable = false)
    private boolean hasAirport;

    @Column(name = "has_terminal", nullable = false)
    private boolean hasTerminal;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected City() {
    }

    public City(Department department, String name, boolean hasAirport, boolean hasTerminal) {
        this.department = department;
        this.name = name;
        this.hasAirport = hasAirport;
        this.hasTerminal = hasTerminal;
        this.active = true;
    }

    public void update(Department department, String name, boolean hasAirport, boolean hasTerminal) {
        this.department = department;
        this.name = name;
        this.hasAirport = hasAirport;
        this.hasTerminal = hasTerminal;
    }

    public UUID getId() { return id; }
    public Department getDepartment() { return department; }
    public String getName() { return name; }
    public boolean hasAirport() { return hasAirport; }
    public boolean hasTerminal() { return hasTerminal; }
    public boolean isActive() { return active; }
    public void deactivate() { this.active = false; }
}
