package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertLifecycleServiceImpl implements AlertLifecycleService {

    private static final Set<String> ACTIVATABLE_STATUSES =
            Set.of("DRAFT", "SCHEDULED");

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
            RequesterContext requester) {

        AlertEntity alert = findAlertForUpdate(id);
        assertManageDrillAllowed(requester, alert.getOrigin());

        Instant now = clock.instant();
        Instant activationAt = request.activationAt();

        if (activationAt == null || !activationAt.isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        String previousStatus = alert.getStatus();

        // A SCHEDULED alert can be rescheduled in place; any other state must
        // follow the normal transition rules.
        if (previousStatus != "SCHEDULED") {
            alertStateMachine.assertTransition(
                    previousStatus,
                    "SCHEDULED");
        }

        alert.setActivationAt(activationAt);
        alert.setStatus("SCHEDULED");
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditActions.SCHEDULE,
                requester.auditActor(),
                previousStatus,
                alert.getStatus(),
                "Alert scheduled for " + activationAt);

        return toResponse(alert);
    }

    @Override
    @Transactional
    public AlertResponse activate(UUID id, RequesterContext requester) {
        return activate(id, requester, CorrelationIds.current());
    }

    @Override
    @Transactional
    public AlertResponse activate(
            UUID id,
            RequesterContext requester,
            String correlationId) {

        AlertEntity alert = findAlertForUpdate(id);
        assertManageDrillAllowed(requester, alert.getOrigin());

        if (alert.getStatus() == "ACTIVE") {
            return toResponse(alert);
        }

        String previousStatus = alert.getStatus();
        alertStateMachine.assertTransition(
                previousStatus,
                "ACTIVE");

        Instant now = clock.instant();

        int updated = alertRepository.activateIfPending(
                id,
                "ACTIVE",
                now,
                ACTIVATABLE_STATUSES);

        if (updated == 0) {
            AlertEntity current = findAlertForUpdate(id);

            if (current.getStatus() == "ACTIVE") {
                return toResponse(current);
            }

            throw new InvalidTransitionException(
                    current.getStatus(),
                    "ACTIVE");
        }

        AlertEntity activated = findAlertForUpdate(id);

        auditService.record(
                activated.getId(),
                AuditActions.ACTIVATE,
                requester.auditActor(),
                previousStatus,
                activated.getStatus(),
                "Alert activated from " + previousStatus);

        eventPublisher.publishEvent(
                new AlertActivatedEvent(
                        activated.getId(),
                        previousStatus,
                        now,
                        correlationId));

        return toResponse(activated);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findDueScheduledAlertIds() {
        return alertRepository.findByStatusAndActivationAtLessThanEqual(
                        "SCHEDULED",
                        clock.instant())
                .stream()
                .map(AlertEntity::getId)
                .toList();
    }

    @Override
    @Transactional
    public AlertResponse archive(UUID id, RequesterContext requester) {
        AlertEntity alert = findAlertForUpdate(id);
        assertManageDrillAllowed(requester, alert.getOrigin());

        if (alert.getStatus() == "ARCHIVED") {
            return toResponse(alert);
        }

        String previousStatus = alert.getStatus();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        alertStateMachine.assertTransition(
                previousStatus,
                "ARCHIVED");

        alert.setStatus("ARCHIVED");
        alert.setArchivedAt(now);
        alert.setUpdatedAt(now);

        alertRepository.saveAndFlush(alert);

        auditService.record(
                alert.getId(),
                AuditActions.ARCHIVE,
                requester.auditActor(),
                previousStatus,
                alert.getStatus(),
                "Alert archived");

        return toResponse(alert);
    }

    private AlertEntity findAlertForUpdate(UUID id) {
        return alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
    }

    private void assertManageDrillAllowed(
            RequesterContext requester,
            AlertOrigin origin) {

        if (requester.isSimulationOnly()
                && origin != AlertOrigin.SIMULATION) {
            throw new ForbiddenOperationException(
                    "El rol ORGANIZADOR solo puede operar alertas "
                            + "asociadas a un simulacro");
        }
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
