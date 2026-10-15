
package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private final AlertRepository alertRepository;
    private final AlertScopeItemRepository scopeItemRepository;
    private final AlertRecipientRepository recipientRepository;
    private final AlertStateMachine stateMachine;
    private final AuditService auditService;
    private final AlertMapper alertMapper;
    private final Clock clock;

    public AlertLifecycleServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertRecipientRepository recipientRepository,
            AlertStateMachine stateMachine,
            AuditService auditService,
            AlertMapper alertMapper,
            Clock clock) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.recipientRepository = recipientRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.alertMapper = alertMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AlertResponse schedule(UUID id, AlertScheduleRequest request, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

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

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }

    @Override
    @Transactional
    public AlertResponse activate(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        Instant now = clock.instant();

        if (alert.getStatus() != AlertStatus.SCHEDULED) {
            throw new InvalidTransitionException(alert.getStatus(), AlertStatus.ACTIVE);
        }

        AlertStatus previousStatus = alert.getStatus();

        stateMachine.assertTransition(previousStatus, AlertStatus.ACTIVE);

        alert.setStatus(AlertStatus.ACTIVE);
        alert.setActivatedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.ACTIVATE,
                actor,
                previousStatus,
                alert.getStatus(),
                "Alerta activada");

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }

    @Override
    @Transactional
    public void activateScheduledAlerts() {
        Instant now = clock.instant();

        var alerts = alertRepository.findByStatusAndActivationAtLessThanEqual(
                AlertStatus.SCHEDULED, now);

        for (AlertEntity alert : alerts) {
            AlertStatus previousStatus = alert.getStatus();

            stateMachine.assertTransition(previousStatus, AlertStatus.ACTIVE);

            alert.setStatus(AlertStatus.ACTIVE);
            alert.setActivatedAt(now);
            alert.setUpdatedAt(now);

            alertRepository.saveAndFlush(alert);

            auditService.record(
                    alert.getId(),
                    AuditAction.ACTIVATE,
                    "system",
                    previousStatus,
                    alert.getStatus(),
                    "Alerta activada automáticamente");
        }
    }

    
    @Override
    @Transactional
    public AlertResponse end(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        if (alert.getStatus() == AlertStatus.ENDED) {
            return alertMapper.toResponse(
                    alert,
                    scopeItemRepository.findByAlertId(id),
                    recipientRepository.findByAlertId(id));
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

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }

    
    @Override
    @Transactional
    public AlertResponse archive(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        if (alert.getStatus() == AlertStatus.ARCHIVED) {
            return alertMapper.toResponse(
                    alert,
                    scopeItemRepository.findByAlertId(id),
                    recipientRepository.findByAlertId(id));
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

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }


}
