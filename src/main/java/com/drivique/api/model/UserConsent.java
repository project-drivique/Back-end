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
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_consents", schema = "iam")
public class UserConsent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "consent_type", nullable = false, length = 60)
    private String consentType;
    @Column(name = "document_version", nullable = false, length = 40)
    private String documentVersion;
    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", nullable = false, updatable = false, columnDefinition = "inet")
    private InetAddress ipAddress;

    protected UserConsent() {}
    public UserConsent(User user, String consentType, String documentVersion, InetAddress ipAddress) {
        this.user = user;
        this.consentType = consentType;
        this.documentVersion = documentVersion;
        this.ipAddress = ipAddress;
        this.acceptedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getConsentType() { return consentType; }
    public String getDocumentVersion() { return documentVersion; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public InetAddress getIpAddress() { return ipAddress; }
}
