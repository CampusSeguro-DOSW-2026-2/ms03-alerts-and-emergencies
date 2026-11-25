package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayloadMapper;
import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.AuditOutboxService;
import edu.eci.dosw.campusseguro.alertas.service.AuditRecord;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private static final int MAX_ACTOR_LENGTH = 64;
    private static final int MAX_DETAILS_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;
    private final AuditOutboxService auditOutboxService;
    private final AuditEventPayloadMapper payloadMapper;
    private final Clock clock;

    public AuditServiceImpl(
            AuditEventRepository auditEventRepository,
            AuditOutboxService auditOutboxService,
            AuditEventPayloadMapper payloadMapper,
            Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.auditOutboxService = auditOutboxService;
        this.payloadMapper = payloadMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditRecord record) {

        Objects.requireNonNull(record, "The audit record must not be null.");
        Objects.requireNonNull(record.alertId(), "Alert ID must not be null.");
        Objects.requireNonNull(record.action(), "Audit action must not be null.");

        if (AuditActions.END.equals(record.action())) {
            throw new IllegalArgumentException(
                    "END is a legacy audit action and cannot be emitted.");
        }

        if (record.actor() == null || record.actor().isBlank()) {
            throw new IllegalArgumentException(
                    "The audit actor must not be null or blank.");
        }

        Instant now = clock.instant();

        AuditEventEntity event = new AuditEventEntity();
        event.setAlertId(record.alertId());
        event.setAction(record.action());
        event.setActor(truncate(record.actor().strip(), MAX_ACTOR_LENGTH));
        event.setFromStatus(record.fromStatus());
        event.setToStatus(record.toStatus());
        event.setDetails(truncate(record.details(), MAX_DETAILS_LENGTH));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        auditEventRepository.save(event);

        if (event.getId() == null) {
            throw new IllegalStateException(
                    "El evento de auditoria se guardo sin identificador.");
        }

        auditOutboxService.enqueue(payloadMapper.toPayload(
                event.getId(),
                record,
                now,
                CorrelationIds.current()));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
