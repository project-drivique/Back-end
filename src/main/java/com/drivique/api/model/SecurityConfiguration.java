package com.drivique.api.model;

import jakarta.persistence.*;
import java.util.UUID;
@Entity
@Table(name = "security_configurations", schema = "iam")
public class SecurityConfiguration {
    @Id private UUID id;
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;
    @Column(name = "config_value", nullable = false, columnDefinition = "text")
    private String configValue;
    protected SecurityConfiguration() {}
    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
}
