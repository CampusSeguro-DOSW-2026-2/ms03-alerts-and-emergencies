package edu.eci.dosw.campusseguro.alertas.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayload;
import edu.eci.dosw.campusseguro.alertas.entity.AuditOutboxEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AuditOutboxRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditOutboxMessage;
import edu.eci.dosw.campusseguro.alertas.service.AuditOutboxService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditOutboxServiceImpl implements AuditOutboxService {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxServiceImpl.class);

    static final String PENDIENTE = "PENDIENTE";
    static final String ENVIANDO = "ENVIANDO";
    static final String ENVIADO = "ENVIADO";
    static final String DESCARTADO = "DESCARTADO";

    private static final int MAX_ERROR_LENGTH = 500;

    /**
     * Serializador propio del payload. No se inyecta el de Spring Boot porque el arranque usa
     * Jackson 3 y este contrato declara sus campos con las anotaciones de Jackson 2.
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditOutboxRepository repository;
    private final Clock clock;
    private final boolean enabled;
    private final int batchSize;
    private final long stuckTimeoutMs;

    public AuditOutboxServiceImpl(
            AuditOutboxRepository repository,
            Clock clock,
            @Value("${app.audit.enabled:true}") boolean enabled,
            @Value("${app.audit.batch-size:50}") int batchSize,
            @Value("${app.audit.stuck-timeout-ms:120000}") long stuckTimeoutMs) {
        this.repository = repository;
        this.clock = clock;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.stuckTimeoutMs = stuckTimeoutMs;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(AuditEventPayload payload) {
        if (!enabled) {
            return;
        }

        Instant now = clock.instant();
        AuditOutboxEntity entity = new AuditOutboxEntity();
        entity.setId(payload.id() == null ? UUID.randomUUID() : UUID.fromString(payload.id()));
        entity.setPayload(serialize(payload));
        entity.setEstado(PENDIENTE);
        entity.setIntentos(0);
        entity.setProximoIntento(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        repository.save(entity);
    }

    @Override
    @Transactional
    public List<AuditOutboxMessage> claimDue() {
        Instant now = clock.instant();
        resetStuck(now);

        return repository.findDue(PENDIENTE, now, PageRequest.of(0, batchSize)).stream()
                .map(entity -> claim(entity, now))
                .toList();
    }

    @Override
    @Transactional
    public void markSent(UUID id) {
        Instant now = clock.instant();
        find(id).ifPresent(entity -> {
            entity.setEstado(ENVIADO);
            entity.setEnviadoEn(now);
            entity.setUltimoError(null);
            entity.setUpdatedAt(now);
        });
    }

    @Override
    @Transactional
    public void markRetry(UUID id, String error, Instant nextAttempt) {
        Instant now = clock.instant();
        find(id).ifPresent(entity -> {
            entity.setEstado(PENDIENTE);
            entity.setProximoIntento(nextAttempt);
            entity.setUltimoError(truncate(error));
            entity.setUpdatedAt(now);
        });
    }

    @Override
    @Transactional
    public void markDiscarded(UUID id, String error) {
        Instant now = clock.instant();
        find(id).ifPresent(entity -> {
            entity.setEstado(DESCARTADO);
            entity.setUltimoError(truncate(error));
            entity.setUpdatedAt(now);
            log.warn("Hecho de auditoria descartado: id={} intentos={} error={}",
                    id, entity.getIntentos(), entity.getUltimoError());
        });
    }

    private AuditOutboxMessage claim(AuditOutboxEntity entity, Instant now) {
        entity.setEstado(ENVIANDO);
        entity.setIntentos(entity.getIntentos() + 1);
        entity.setUpdatedAt(now);

        return new AuditOutboxMessage(entity.getId(), entity.getPayload(), entity.getIntentos());
    }

    /** Devuelve a pendientes los lotes que un reinicio del proceso dejó en envío. */
    private void resetStuck(Instant now) {
        Instant threshold = now.minusMillis(stuckTimeoutMs);
        for (AuditOutboxEntity entity
                : repository.findStuck(ENVIANDO, threshold, PageRequest.of(0, batchSize))) {
            entity.setEstado(PENDIENTE);
            entity.setUpdatedAt(now);
            log.warn("Hecho de auditoria devuelto a pendientes: id={} intentos={}",
                    entity.getId(), entity.getIntentos());
        }
    }

    private Optional<AuditOutboxEntity> find(UUID id) {
        Optional<AuditOutboxEntity> entity = repository.findById(id);
        if (entity.isEmpty()) {
            log.warn("No se encontro el hecho de auditoria {}", id);
        }
        return entity;
    }

    private String serialize(AuditEventPayload payload) {
        try {
            return JSON.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el hecho de auditoria", ex);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
