package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AlertLifecycleServiceImpl.class);

    private final AlertRepository alertRepository;
    private final AlertScopeItemRepository scopeItemRepository;
    private final AlertRecipientRepository recipientRepository;
    private final AlertStateMachine stateMachine;
    private final AuditService auditService;
    private final AlertMapper alertMapper;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AlertLifecycleServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertRecipientRepository recipientRepository,
            AlertStateMachine stateMachine,
            AuditService auditService,
            AlertMapper alertMapper,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.recipientRepository = recipientRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.alertMapper = alertMapper;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AlertResponse schedule(UUID id, AlertScheduleRequest request, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        assertNotArchived(alert);

        Instant now = clock.instant();
        Instant activationAt = request.activationAt();

        if (activationAt == null || !activationAt.isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        stateMachine.assertTransition(alert.getStatus(), AlertStatus.SCHEDULED);

        AlertStatus previousStatus = alert.getStatus();

        alert.setActivationAt(activationAt);
        alert.setStatus(AlertStatus.SCHEDULED);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.SCHEDULE,
                actor,
                previousStatus,
                alert.getStatus(),
                "Alerta programada para " + activationAt);

        return toResponse(alert);
    }

    @Override
    @Transactional
    public AlertResponse activate(UUID id, String actor) {
        AlertStatus previousStatus = alertRepository.findStatusById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        if (previousStatus == AlertStatus.ACTIVE) {
            return toResponse(findExisting(id));
        }

        assertNotArchived(previousStatus);

        stateMachine.assertTransition(previousStatus, AlertStatus.ACTIVE);

        Instant now = clock.instant();
        int updated = alertRepository.activateIfPending(
                id,
                now,
                AlertStatus.ACTIVE,
                List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED));

        if (updated == 0) {
            AlertStatus currentStatus = alertRepository.findStatusById(id)
                    .orElseThrow(() -> new AlertNotFoundException(id));
            if (currentStatus == AlertStatus.ACTIVE) {
                return toResponse(findExisting(id));
            }
            throw new InvalidTransitionException(currentStatus, AlertStatus.ACTIVE);
        }

        auditService.record(
                id,
                AuditAction.ACTIVATE,
                actor,
                previousStatus,
                AlertStatus.ACTIVE,
                ActorResolver.SYSTEM_SCHEDULER.equals(actor)
                        ? "Alerta activada automáticamente"
                        : "Alerta activada");

        eventPublisher.publishEvent(new AlertActivatedEvent(id, previousStatus, now));

        return toResponse(findExisting(id));
    }

    @Override
    @Transactional
    public void activateScheduledAlerts() {
        Instant now = clock.instant();

        var alerts = alertRepository.findByStatusAndActivationAtLessThanEqual(
                AlertStatus.SCHEDULED, now);

        for (AlertEntity alert : alerts) {
            try {
                activate(alert.getId(), ActorResolver.SYSTEM_SCHEDULER);
            } catch (RuntimeException ex) {
                log.warn("No se pudo activar la alerta {}", alert.getId(), ex);
            }
        }
    }

    @Override
    @Transactional
    public AlertResponse end(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        assertNotArchived(alert);

        if (alert.getStatus() == AlertStatus.ENDED) {
            return toResponse(alert);
        }

        AlertStatus previousStatus = alert.getStatus();

        stateMachine.assertTransition(previousStatus, AlertStatus.ENDED);

        Instant now = clock.instant();

        alert.setStatus(AlertStatus.ENDED);
        alert.setEndedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.END,
                actor,
                previousStatus,
                alert.getStatus(),
                "Alerta finalizada");

        return toResponse(alert);
    }

    @Override
    @Transactional
    public AlertResponse archive(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        if (alert.getStatus() == AlertStatus.ARCHIVED) {
            return toResponse(alert);
        }

        AlertStatus previousStatus = alert.getStatus();

        stateMachine.assertTransition(previousStatus, AlertStatus.ARCHIVED);

        Instant now = clock.instant();

        alert.setStatus(AlertStatus.ARCHIVED);
        alert.setArchivedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.ARCHIVE,
                actor,
                previousStatus,
                alert.getStatus(),
                "Alerta archivada");

        return toResponse(alert);
    }

    private AlertEntity findExisting(UUID id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    private AlertResponse toResponse(AlertEntity alert) {
        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(alert.getId()),
                recipientRepository.findByAlertId(alert.getId()));
    }

    private void assertNotArchived(AlertEntity alert) {
        assertNotArchived(alert.getStatus());
    }

    private void assertNotArchived(AlertStatus status) {
        if (status == AlertStatus.ARCHIVED) {
            throw new InvalidAlertStateException(status);
        }
    }
}
