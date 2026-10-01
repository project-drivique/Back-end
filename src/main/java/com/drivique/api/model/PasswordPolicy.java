package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "password_policies", schema = "iam")
public class PasswordPolicy {
    @Id private UUID id;
    @Column(name = "min_length", nullable = false)
    private short minLength;
    @Column(name = "require_uppercase", nullable = false)
    private boolean requireUppercase;
    @Column(name = "require_number", nullable = false)
    private boolean requireNumber;
    @Column(name = "require_symbol", nullable = false)
    private boolean requireSymbol;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    protected PasswordPolicy() {}
    public short getMinLength() { return minLength; }
    public boolean isRequireUppercase() { return requireUppercase; }
    public boolean isRequireNumber() { return requireNumber; }
    public boolean isRequireSymbol() { return requireSymbol; }
}
