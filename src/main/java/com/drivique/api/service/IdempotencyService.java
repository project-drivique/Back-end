package com.drivique.api.service;

import com.drivique.api.model.IdempotencyKey;
import com.drivique.api.repository.IdempotencyKeyRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Protocolo Idempotency-Key. Cada paso corre en su propia transacción corta (confirmada al instante) para que una
 * segunda petición con la misma clave —aunque llegue en el mismo milisegundo— la vea o choque con la restricción única.
 */
@Service
public class IdempotencyService {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyService.class);

    /** Resultado de intentar reservar la clave. */
    public sealed interface Claim permits Claim.Started, Claim.Replay, Claim.Conflict, Claim.Pending {
        /** Primera vez: se ejecuta la operación y luego se llama a complete/abandon con este id. */
        record Started(UUID id) implements Claim {}
        /** Ya se resolvió antes: se devuelve la respuesta guardada. */
        record Replay(int status, String contentType, String body) implements Claim {}
        /** La misma clave con otra petición distinta. */
        record Conflict() implements Claim {}
        /** Otra petición con la misma clave sigue ejecutándose. */
        record Pending() implements Claim {}
    }

    private final IdempotencyKeyRepository repository;
    private final TransactionTemplate tx;
    private final Duration ttl;

    public IdempotencyService(
            IdempotencyKeyRepository repository,
            PlatformTransactionManager txManager,
            @Value("${app.idempotency.ttl-hours:24}") long ttlHours
    ) {
        this.repository = repository;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** Intenta reservar la clave para esta persona; si ya existe, decide si repetir la respuesta, rechazar o esperar. */
    public Claim claim(String userEmail, String key, String method, String path, String requestHash) {
        try {
            UUID id = tx.execute(status -> repository.saveAndFlush(
                    new IdempotencyKey(userEmail, key, method, path, requestHash, Instant.now().plus(ttl))).getId());
            return new Claim.Started(id);
        } catch (DataIntegrityViolationException duplicate) {
            // La clave ya existía (restricción única): se mira qué pasó con ella
        }
        Optional<IdempotencyKey> existing = tx.execute(status -> repository.findByUserEmailAndIdempotencyKey(userEmail, key));
        if (existing == null || existing.isEmpty()) {
            // Se borró entre medias (venció o se abandonó): se reintenta una vez
            return claimAgain(userEmail, key, method, path, requestHash);
        }
        IdempotencyKey saved = existing.get();
        if (saved.getExpiresAt().isBefore(Instant.now())) {
            tx.executeWithoutResult(status -> repository.delete(saved));
            return claimAgain(userEmail, key, method, path, requestHash);
        }
        if (!saved.getRequestHash().equals(requestHash) || !saved.getRequestPath().equals(path) || !saved.getHttpMethod().equals(method)) {
            return new Claim.Conflict();
        }
        if (saved.isCompleted()) {
            return new Claim.Replay(saved.getResponseStatus(), saved.getResponseContentType(), saved.getResponseBody());
        }
        return new Claim.Pending();
    }

    private Claim claimAgain(String userEmail, String key, String method, String path, String requestHash) {
        try {
            UUID id = tx.execute(status -> repository.saveAndFlush(
                    new IdempotencyKey(userEmail, key, method, path, requestHash, Instant.now().plus(ttl))).getId());
            return new Claim.Started(id);
        } catch (DataIntegrityViolationException stillThere) {
            return new Claim.Pending();
        }
    }

    /** Consulta si una clave que estaba en curso ya terminó (para esperar a la primera petición en vez de fallar). */
    public Optional<Claim.Replay> findCompleted(String userEmail, String key) {
        Optional<IdempotencyKey> existing = tx.execute(status -> repository.findByUserEmailAndIdempotencyKey(userEmail, key));
        if (existing == null) {
            return Optional.empty();
        }
        return existing.filter(IdempotencyKey::isCompleted)
                .map(k -> new Claim.Replay(k.getResponseStatus(), k.getResponseContentType(), k.getResponseBody()));
    }

    public void complete(UUID id, int status, String contentType, String body) {
        tx.executeWithoutResult(t -> repository.findById(id).ifPresent(k -> {
            k.complete(status, contentType, body);
            repository.save(k);
        }));
    }

    /** La operación falló por un error del servidor: se libera la clave para que el reintento pueda ejecutarse. */
    public void abandon(UUID id) {
        tx.executeWithoutResult(t -> repository.deleteById(id));
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-ms:3600000}", initialDelayString = "${app.idempotency.cleanup-initial-ms:60000}")
    public void purgeExpired() {
        try {
            int removed = tx.execute(status -> repository.deleteExpired(Instant.now()));
            if (removed > 0) {
                LOG.info("Claves de idempotencia vencidas eliminadas: {}", removed);
            }
        } catch (RuntimeException ex) {
            LOG.warn("No se pudieron limpiar las claves de idempotencia vencidas: {}", ex.getMessage());
        }
    }
}
