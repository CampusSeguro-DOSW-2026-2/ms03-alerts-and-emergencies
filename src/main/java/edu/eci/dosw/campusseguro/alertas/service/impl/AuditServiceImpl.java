
package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private static final int MAX_ACTOR_LENGTH = 64;
    private static final int MAX_DETAILS_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditServiceImpl(
            AuditEventRepository auditEventRepository,
            Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(
            UUID alertId,
            String action,
            String actor,
            String fromStatus,
            String toStatus,
            String details) {

        Objects.requireNonNull(alertId, "Alert ID must not be null.");
        Objects.requireNonNull(action, "Audit action must not be null.");

        if (AuditActions.END.equals(action)) {
            throw new IllegalArgumentException(
                    "END is a legacy audit action and cannot be emitted.");
        }

        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(
                    "The audit actor must not be null or blank.");
        }

        Instant now = clock.instant();

        AuditEventEntity event = new AuditEventEntity();
        event.setAlertId(alertId);
        event.setAction(action);
        event.setActor(truncate(actor.strip(), MAX_ACTOR_LENGTH));
        event.setFromStatus(toString(fromStatus));
        event.setToStatus(toString(toStatus));
        event.setDetails(truncate(details, MAX_DETAILS_LENGTH));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        auditEventRepository.save(event);
    }

    private String toString(String status) {
        return status == null
                ? null
                : String.valueOf(status);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}