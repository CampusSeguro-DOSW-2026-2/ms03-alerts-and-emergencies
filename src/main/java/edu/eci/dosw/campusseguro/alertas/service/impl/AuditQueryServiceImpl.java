package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AuditQueryService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditEventRepository auditEventRepository;
    private final AlertRepository alertRepository;

    public AuditQueryServiceImpl(
            AuditEventRepository auditEventRepository,
            AlertRepository alertRepository) {
        this.auditEventRepository = auditEventRepository;
        this.alertRepository = alertRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventResponse> findByAlert(UUID alertId) {
        if (!alertRepository.existsById(alertId)) {
            throw new AlertNotFoundException(alertId);
        }
        return auditEventRepository.findByAlertIdOrderByCreatedAtAsc(alertId).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponse toResponse(AuditEventEntity event) {
        return new AuditEventResponse(
                event.getId(),
                event.getAlertId(),
                event.getActor(),
                actorType(event.getActor()),
                event.getAction(),
                event.getFromStatus(),
                event.getToStatus(),
                event.getDetails(),
                event.getCreatedAt());
    }

    private String actorType(String actor) {
        if (ActorResolver.SYSTEM_SCHEDULER.equals(actor)) {
            return "SYSTEM";
        }
        if (actor != null && actor.startsWith("ms06:")) {
            return "SERVICE";
        }
        return "USER";
    }
}