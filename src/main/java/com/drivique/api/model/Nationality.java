package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "nationalities", schema = "iam")
public class Nationality {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "iso_code", nullable = false, length = 2, columnDefinition = "bpchar(2)")
    private String isoCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    protected Nationality() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getIsoCode() { return isoCode; }
    public boolean isActive() { return active; }
}
