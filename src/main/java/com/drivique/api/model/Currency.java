package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "currencies", schema = "core")
public class Currency {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 3)
    private String code;
    @Column(nullable = false, unique = true, length = 50)
    private String name;
    @Column(nullable = false, length = 5)
    private String symbol;
    @Column(name = "is_default", nullable = false)
    private boolean defaultCurrency;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    protected Currency() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public boolean getDefaultCurrency() { return defaultCurrency; }
    public boolean getActive() { return active; }
}
