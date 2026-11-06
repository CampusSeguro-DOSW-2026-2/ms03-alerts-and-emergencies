
package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertRequestException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.exception.SimulationMismatchException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.Role;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private static final EnumSet<AlertStatus> ACTIVATABLE_STATUSES =
            EnumSet.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED);

    private final AlertRepository alertRepository;
    private final AlertScopeItemRepository scopeItemRepository;
    private final AlertRecipientRepository recipientRepository;
    private final AlertMapper alertMapper;
    private final AuditService auditService;
    private final AlertStateMachine alertStateMachine;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public AlertLifecycleServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertRecipientRepository recipientRepository,
            AlertMapper alertMapper,
            AuditService auditService,
            AlertStateMachine alertStateMachine,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.recipientRepository = recipientRepository;
        this.alertMapper = alertMapper;
        this.auditService = auditService;
        this.alertStateMachine = alertStateMachine;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AlertResponse schedule(
            UUID id,
            AlertScheduleRequest request,
            String actor) {

        AlertEntity alert = findAlertForUpdate(id);

        Instant now = clock.instant();
        Instant activationAt = request.activationAt();

        if (activationAt == null || !activationAt.isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        AlertStatus previousStatus = alert.getStatus();

        alertStateMachine.assertTransition(
                previousStatus,
                AlertStatus.SCHEDULED);

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
                "Alert scheduled for " + activationAt);

        return toResponse(alert);
    }

    @Override
    @Transactional
    public AlertResponse activate(UUID id, String actor) {
        AlertEntity alert = findAlertForUpdate(id);

        if (alert.getStatus() == AlertStatus.ACTIVE) {
            return toResponse(alert);
        }

        AlertStatus previousStatus = alert.getStatus();
        alertStateMachine.assertTransition(
                previousStatus,
                AlertStatus.ACTIVE);

        Instant now = clock.instant();

        int updated = alertRepository.activateIfPending(
                id,
                AlertStatus.ACTIVE,
                now,
                ACTIVATABLE_STATUSES);

        if (updated == 0) {
            AlertEntity current = findAlertForUpdate(id);

            if (current.getStatus() == AlertStatus.ACTIVE) {
                return toResponse(current);
            }

            throw new InvalidTransitionException(
                    current.getStatus(),
                    AlertStatus.ACTIVE);
        }

        AlertEntity activated = findAlertForUpdate(id);

        auditService.record(
                activated.getId(),
                AuditAction.ACTIVATE,
                actor,
                previousStatus,
                activated.getStatus(),
                "Alert activated from " + previousStatus);

        eventPublisher.publishEvent(
                new AlertActivatedEvent(
                        activated.getId(),
                        previousStatus,
                        now));

        return toResponse(activated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueScheduledAlertIds() {
        return alertRepository.findByStatusAndActivationAtLessThanEqual(
                        AlertStatus.SCHEDULED,
                        clock.instant())
                .stream()
                .map(AlertEntity::getId)
                .toList();
    }

    @Override
    @Transactional
    public AlertResponse end(
            UUID id,
            AlertEndRequest request,
            String actor) {

        AlertEntity alert = findAlertForUpdate(id);

        if (Role.SERVICE_MS06.name().equals(actor)) {
            if (request == null || request.simulationId() == null) {
                throw new InvalidAlertRequestException(
                        "simulationId",
                        "simulationId is required for SERVICE_MS06");
            }

            if (!Objects.equals(
                    request.simulationId(),
                    alert.getSimulationId())) {
                throw new SimulationMismatchException(
                        alert.getId(),
                        request.simulationId());
            }
        }

        if (alert.getStatus() == AlertStatus.ENDED) {
            return toResponse(alert);
        }

        AlertStatus previousStatus = alert.getStatus();
        Instant now = clock.instant();

        alertStateMachine.assertTransition(
                previousStatus,
                AlertStatus.ENDED);

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
                "Alert ended");

        return toResponse(alert);
    }

    @Override
    @Transactional
    public AlertResponse archive(UUID id, String actor) {
        AlertEntity alert = findAlertForUpdate(id);

        AlertStatus previousStatus = alert.getStatus();
        Instant now = clock.instant();

        alertStateMachine.assertTransition(
                previousStatus,
                AlertStatus.ARCHIVED);

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
                "Alert archived");

        return toResponse(alert);
    }

    private AlertEntity findAlertForUpdate(UUID id) {
        return alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    private AlertResponse toResponse(AlertEntity alert) {
        List<AlertScopeItemEntity> scopeItems =
                scopeItemRepository.findByAlertId(alert.getId());

        List<AlertRecipientEntity> recipients =
                recipientRepository.findByAlertId(alert.getId());

        return alertMapper.toResponse(
                alert,
                scopeItems,
                recipients);
    }
}
