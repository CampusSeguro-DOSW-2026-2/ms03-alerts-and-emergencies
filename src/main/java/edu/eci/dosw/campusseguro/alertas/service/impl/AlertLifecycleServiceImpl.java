package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.DuplicateSimulationAlertException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertRequestException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.exception.SimulationMismatchException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.security.Role;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AlertLifecycleServiceImpl.class);

    private final AlertRepository alertRepository;
    private final AlertStateMachine stateMachine;
    private final AuditService auditService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate requiresNewTransaction;

    public AlertLifecycleServiceImpl(
            AlertRepository alertRepository,
            AlertStateMachine stateMachine,
            AuditService auditService,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public StateChangeResponse schedule(UUID id, AlertScheduleRequest request, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        assertNotArchived(alert);

        Instant now = clock.instant();
        Instant activationAt = request.activationAt();

        if (activationAt == null || !activationAt.isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        stateMachine.assertTransition(alert.getStatus(), AlertStatus.SCHEDULED);
        assertNoOtherLiveSimulation(alert);

        AlertStatus previousStatus = alert.getStatus();

        alert.setActivationAt(activationAt);
        alert.setStatus(AlertStatus.SCHEDULED);
        alert.setUpdatedAt(now);

        try {
            alertRepository.saveAndFlush(alert);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateOrRethrow(alert, ex);
        }

        auditService.record(
                alert.getId(),
                AuditAction.SCHEDULE,
                actor,
                previousStatus,
                AlertStatus.SCHEDULED,
                "Alerta programada para " + activationAt);

        return new StateChangeResponse(alert.getId(), previousStatus, AlertStatus.SCHEDULED, now);
    }

    @Override
    @Transactional
    public StateChangeResponse activate(UUID id, String actor) {
        AlertEntity alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
        AlertStatus previousStatus = alert.getStatus();

        Instant now = clock.instant();

        if (previousStatus == AlertStatus.ACTIVE) {
            return new StateChangeResponse(id, AlertStatus.ACTIVE, AlertStatus.ACTIVE, now);
        }

        assertNotArchived(previousStatus);

        stateMachine.assertTransition(previousStatus, AlertStatus.ACTIVE);
        assertNoOtherLiveSimulation(alert);

        int updated;
        try {
            updated = alertRepository.activateIfPending(
                    id,
                    now,
                    AlertStatus.ACTIVE,
                    List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED));
        } catch (DataIntegrityViolationException ex) {
            throw duplicateOrRethrow(alert, ex);
        }

        if (updated == 0) {
            AlertStatus currentStatus = alertRepository.findStatusById(id)
                    .orElseThrow(() -> new AlertNotFoundException(id));
            if (currentStatus == AlertStatus.ACTIVE) {
                return new StateChangeResponse(id, AlertStatus.ACTIVE, AlertStatus.ACTIVE, now);
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

        return new StateChangeResponse(id, previousStatus, AlertStatus.ACTIVE, now);
    }

    @Override
    public void activateScheduledAlerts() {
        Instant now = clock.instant();

        var alerts = alertRepository.findByStatusAndActivationAtLessThanEqual(
                AlertStatus.SCHEDULED, now);

        for (AlertEntity alert : alerts) {
            try {
                requiresNewTransaction.executeWithoutResult(
                        status -> activate(alert.getId(), ActorResolver.SYSTEM_SCHEDULER));
            } catch (RuntimeException ex) {
                log.warn("No se pudo activar la alerta {}", alert.getId(), ex);
            }
        }
    }

    @Override
    @Transactional
    public StateChangeResponse end(UUID id, String actor) {
        return end(id, null, actor);
    }

    @Override
    @Transactional
    public StateChangeResponse end(UUID id, AlertEndRequest request, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        assertNotArchived(alert);
        assertSimulationMatches(alert, request, actor);

        Instant now = clock.instant();

        if (alert.getStatus() == AlertStatus.ENDED) {
            Instant changedAt = alert.getEndedAt() == null ? now : alert.getEndedAt();
            return new StateChangeResponse(id, AlertStatus.ENDED, AlertStatus.ENDED, changedAt);
        }

        AlertStatus previousStatus = alert.getStatus();

        stateMachine.assertTransition(previousStatus, AlertStatus.ENDED);

        alert.setStatus(AlertStatus.ENDED);
        alert.setEndedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.END,
                actor,
                previousStatus,
                AlertStatus.ENDED,
                "Alerta finalizada");

        return new StateChangeResponse(id, previousStatus, AlertStatus.ENDED, now);
    }

    @Override
    @Transactional
    public StateChangeResponse archive(UUID id, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        Instant now = clock.instant();

        if (alert.getStatus() == AlertStatus.ARCHIVED) {
            Instant changedAt = alert.getArchivedAt() == null ? now : alert.getArchivedAt();
            return new StateChangeResponse(id, AlertStatus.ARCHIVED, AlertStatus.ARCHIVED, changedAt);
        }

        AlertStatus previousStatus = alert.getStatus();

        stateMachine.assertTransition(previousStatus, AlertStatus.ARCHIVED);

        alert.setStatus(AlertStatus.ARCHIVED);
        alert.setArchivedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditAction.ARCHIVE,
                actor,
                previousStatus,
                AlertStatus.ARCHIVED,
                "Alerta archivada");

        return new StateChangeResponse(id, previousStatus, AlertStatus.ARCHIVED, now);
    }

    private void assertNoOtherLiveSimulation(AlertEntity alert) {
        DuplicateSimulationAlertException conflict = liveSimulationConflict(alert);
        if (conflict != null) {
            throw conflict;
        }
    }

    private RuntimeException duplicateOrRethrow(AlertEntity alert, DataIntegrityViolationException ex) {
        if (alert.getSimulationId() == null) {
            return ex;
        }
        DuplicateSimulationAlertException conflict =
                requiresNewTransaction.execute(status -> liveSimulationConflict(alert));
        return conflict != null ? conflict : ex;
    }

    private DuplicateSimulationAlertException liveSimulationConflict(AlertEntity alert) {
        if (alert.getSimulationId() == null) {
            return null;
        }
        return alertRepository.findFirstBySimulationIdAndStatusIn(
                        alert.getSimulationId(), List.of(AlertStatus.SCHEDULED, AlertStatus.ACTIVE))
                .filter(existing -> !existing.getId().equals(alert.getId()))
                .map(existing -> new DuplicateSimulationAlertException(
                        alert.getSimulationId(), existing.getId()))
                .orElse(null);
    }

    private void assertSimulationMatches(AlertEntity alert, AlertEndRequest request, String actor) {
        String simulationId = request == null || request.simulationId() == null
                ? null
                : request.simulationId().strip();
        if (simulationId != null && simulationId.isEmpty()) {
            simulationId = null;
        }

        if (Role.SERVICE_MS06.name().equals(actor) && simulationId == null) {
            throw new InvalidAlertRequestException(
                    "simulationId", "El servicio MS06 debe indicar el ID del simulacro");
        }

        if (simulationId != null
                && (alert.getOrigin() != AlertOrigin.SIMULATION
                || !simulationId.equals(alert.getSimulationId()))) {
            throw new SimulationMismatchException(alert.getId(), simulationId);
        }
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
