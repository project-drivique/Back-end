package com.drivique.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Historial de cambios de un parámetro global: permite saber quién lo cambió y revertirlo. */
@Entity
@Table(name = "security_configuration_changes", schema = "iam")
public class SecurityConfigurationChange {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "config_key", nullable = false, length = 100) private String configKey;
    @Column(name = "old_value", columnDefinition = "text") private String oldValue;
    @Column(name = "new_value", nullable = false, columnDefinition = "text") private String newValue;
    @Column(name = "reason", length = 300) private String reason;
    @Column(name = "reverted", nullable = false) private boolean reverted;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by", nullable = false) private User changedBy;
    @Column(name = "changed_at", nullable = false) private Instant changedAt = Instant.now();

    protected SecurityConfigurationChange() {}

    public SecurityConfigurationChange(String configKey, String oldValue, String newValue, String reason, boolean reverted, User changedBy) {
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.reason = reason;
        this.reverted = reverted;
        this.changedBy = changedBy;
    }

    public UUID getId() { return id; }
    public String getConfigKey() { return configKey; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getReason() { return reason; }
    public boolean isReverted() { return reverted; }
    public void markReverted() { this.reverted = true; }
    public User getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
