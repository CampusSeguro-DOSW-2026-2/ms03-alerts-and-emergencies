package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditServiceImpl implements AuditService {

    private static final int ACTOR_MAX_LENGTH = 64;
    private static final int DETAILS_MAX_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditServiceImpl(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID alertId, AuditAction action, String actor, AlertStatus fromStatus, AlertStatus toStatus, String details) {
        Instant now = clock.instant();
        AuditEventEntity event = new AuditEventEntity();
        event.setAlertId(alertId);
        event.setAction(action);
        event.setActor(truncate(actor, ACTOR_MAX_LENGTH));
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setDetails(truncate(details, DETAILS_MAX_LENGTH));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        auditEventRepository.save(event);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
