package com.drivique.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "support")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "criticality", nullable = false, length = 20)
    private String criticality = "NORMAL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Notification() {
    }

    public Notification(User user, String channel, String type, String subject, String message, UUID referenceId) {
        this.user = user;
        this.channel = channel != null ? channel.trim().toUpperCase() : "IN_APP";
        this.type = type != null ? type.trim().toUpperCase() : "GENERAL";
        this.subject = subject != null ? subject.trim() : "";
        this.message = message != null ? message.trim() : "";
        this.referenceId = referenceId;
        this.isRead = false;
        this.sentAt = Instant.now();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Notification(User user, String channel, String type, String subject, String message, UUID referenceId,
                        String eventType, String criticality, String metadata, String idempotencyKey) {
        this(user, channel, type, subject, message, referenceId);
        this.eventType = eventType;
        this.criticality = criticality != null ? criticality.trim().toUpperCase() : "NORMAL";
        this.metadata = metadata;
        this.idempotencyKey = idempotencyKey;
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getCriticality() {
        return criticality;
    }

    public void setCriticality(String criticality) {
        this.criticality = criticality;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
