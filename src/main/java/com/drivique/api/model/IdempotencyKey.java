package com.drivique.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Resultado guardado de una operación identificada por la cabecera Idempotency-Key. Si la persona repite la petición con
 * la misma clave se devuelve esta respuesta sin volver a ejecutar la reserva o el cobro.
 */
@Entity
@Table(
        name = "idempotency_keys",
        schema = "support",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_keys_scope", columnNames = {"user_email", "idempotency_key"})
)
public class IdempotencyKey {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_email", nullable = false, length = 254)
    private String userEmail;

    @Column(name = "idempotency_key", nullable = false, length = 80)
    private String idempotencyKey;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "status", nullable = false, length = 12)
    private String status = IN_PROGRESS;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_content_type", length = 100)
    private String responseContentType;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String userEmail, String idempotencyKey, String httpMethod, String requestPath, String requestHash, Instant expiresAt) {
        this.userEmail = userEmail;
        this.idempotencyKey = idempotencyKey;
        this.httpMethod = httpMethod;
        this.requestPath = requestPath;
        this.requestHash = requestHash;
        this.expiresAt = expiresAt;
    }

    public void complete(int responseStatus, String contentType, String body) {
        this.status = COMPLETED;
        this.responseStatus = responseStatus;
        this.responseContentType = contentType;
        this.responseBody = body;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getHttpMethod() { return httpMethod; }
    public String getRequestPath() { return requestPath; }
    public String getRequestHash() { return requestHash; }
    public String getStatus() { return status; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseContentType() { return responseContentType; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isCompleted() { return COMPLETED.equals(status); }
}
