package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_delivery_attempts", schema = "support")
public class NotificationDeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "outbox_id", nullable = false)
    private NotificationOutbox outbox;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected NotificationDeliveryAttempt() {
    }

    public NotificationDeliveryAttempt(NotificationOutbox outbox, int attemptNumber, String provider, String status,
                                       String providerMessageId, String errorCode, String errorMessage) {
        this.outbox = outbox;
        this.attemptNumber = attemptNumber;
        this.provider = provider != null ? provider.trim() : "UNKNOWN";
        this.status = status != null ? status.trim().toUpperCase() : "FAILED";
        this.providerMessageId = providerMessageId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.attemptedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public NotificationOutbox getOutbox() {
        return outbox;
    }

    public void setOutbox(NotificationOutbox outbox) {
        this.outbox = outbox;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
