package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "admin";

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertStateMachine stateMachine;

    @Mock
    private AuditService auditService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlertLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertLifecycleServiceImpl(
                alertRepository,
                stateMachine,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher,
                new DirectTransactionManager());
    }

    private AlertEntity alertWithStatus(AlertStatus status) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setStatus(status);
        return alert;
    }

    private AlertEntity alertWithStatus(AlertStatus status, UUID id) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", id);
        alert.setStatus(status);
        return alert;
    }

    private AlertScheduleRequest requestWithActivation(Instant activationAt) {
        AlertScheduleRequest request = new AlertScheduleRequest(activationAt);
        return request;
    }

    @Test
    void schedule_shouldScheduleAlert_whenAlertIsDraftAndDateIsInTheFuture() {
        AlertEntity alert = alertWithStatus(AlertStatus.DRAFT);
        Instant activationAt = NOW.plus(1, java.time.temporal.ChronoUnit.HOURS);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        StateChangeResponse result = service.schedule(ID, requestWithActivation(activationAt), ACTOR);

        assertEquals(ID, result.idAlerta());
        assertEquals(AlertStatus.DRAFT, result.estadoAnterior());
        assertEquals(AlertStatus.SCHEDULED, result.estadoNuevo());
        assertEquals(NOW, result.fechaCambio());
        assertEquals(AlertStatus.SCHEDULED, alert.getStatus());
        assertEquals(activationAt, alert.getActivationAt());

        InOrder order = inOrder(stateMachine, alertRepository, auditService);
        order.verify(stateMachine).assertTransition(AlertStatus.DRAFT, AlertStatus.SCHEDULED);
        order.verify(alertRepository).saveAndFlush(alert);
        order.verify(auditService).record(
                ID,
                AuditAction.SCHEDULE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.SCHEDULED,
                "Alerta programada para " + activationAt);
    }

    @Test
    void schedule_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(
                AlertNotFoundException.class,
                () -> service.schedule(ID, requestWithActivation(NOW.plusSeconds(60)), ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldRejectPastOrEqualActivationDate() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alertWithStatus(AlertStatus.DRAFT)));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, requestWithActivation(NOW), ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldNotSaveNorAudit_whenTransitionIsInvalid() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        doThrow(new InvalidTransitionException(AlertStatus.ACTIVE, AlertStatus.SCHEDULED))
                .when(stateMachine)
                .assertTransition(AlertStatus.ACTIVE, AlertStatus.SCHEDULED);

        assertThrows(
                InvalidTransitionException.class,
                () -> service.schedule(ID, requestWithActivation(NOW.plusSeconds(60)), ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activate_shouldActivateAlert_whenAlertIsScheduled() {
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.SCHEDULED));
        when(alertRepository.activateIfPending(
                        eq(ID),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);

        StateChangeResponse result = service.activate(ID, ACTOR);

        assertEquals(AlertStatus.SCHEDULED, result.estadoAnterior());
        assertEquals(AlertStatus.ACTIVE, result.estadoNuevo());
        assertEquals(NOW, result.fechaCambio());
        verify(stateMachine).assertTransition(AlertStatus.SCHEDULED, AlertStatus.ACTIVE);
        verify(auditService).record(
                ID,
                AuditAction.ACTIVATE,
                ACTOR,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alerta activada");
        verify(eventPublisher).publishEvent(any(AlertActivatedEvent.class));
    }

    @Test
    void activate_shouldActivateAlert_whenAlertIsDraft() {
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.DRAFT));
        when(alertRepository.activateIfPending(
                        eq(ID),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);

        StateChangeResponse result = service.activate(ID, ACTOR);

        assertEquals(AlertStatus.DRAFT, result.estadoAnterior());
        assertEquals(AlertStatus.ACTIVE, result.estadoNuevo());
        verify(stateMachine).assertTransition(AlertStatus.DRAFT, AlertStatus.ACTIVE);
    }

    @Test
    void activate_shouldReturnCurrentState_whenAlertIsAlreadyActive() {
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.ACTIVE));

        StateChangeResponse result = service.activate(ID, ACTOR);

        assertEquals(AlertStatus.ACTIVE, result.estadoAnterior());
        assertEquals(AlertStatus.ACTIVE, result.estadoNuevo());
        verifyNoInteractions(stateMachine, auditService, eventPublisher);
        verify(alertRepository, never()).activateIfPending(any(), any(), any(), anyList());
    }

    @Test
    void activate_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.activate(ID, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void activate_shouldThrowInvalidTransition_whenAlertCannotBeActivated() {
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.ENDED));
        doThrow(new InvalidTransitionException(AlertStatus.ENDED, AlertStatus.ACTIVE))
                .when(stateMachine)
                .assertTransition(AlertStatus.ENDED, AlertStatus.ACTIVE);

        assertThrows(InvalidTransitionException.class, () -> service.activate(ID, ACTOR));

        verify(alertRepository, never()).activateIfPending(any(), any(), any(), anyList());
        verifyNoInteractions(auditService);
    }

    @Test
    void activateScheduledAlerts_shouldActivateDueAlertsAndContinueAfterFailure() {
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AlertEntity first = alertWithStatus(AlertStatus.SCHEDULED, ID);
        AlertEntity second = alertWithStatus(AlertStatus.SCHEDULED, secondId);

        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of(first, second));
        when(alertRepository.findStatusById(ID)).thenThrow(new IllegalStateException("boom"));
        when(alertRepository.findStatusById(secondId)).thenReturn(Optional.of(AlertStatus.SCHEDULED));
        when(alertRepository.activateIfPending(
                        eq(secondId),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);

        service.activateScheduledAlerts();

        verify(auditService).record(
                secondId,
                AuditAction.ACTIVATE,
                ActorResolver.SYSTEM_SCHEDULER,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alerta activada automáticamente");
        verify(eventPublisher).publishEvent(any(AlertActivatedEvent.class));
    }

    @Test
    void activateScheduledAlerts_shouldNotActivateAnything_whenThereAreNoDueAlerts() {
        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of());

        service.activateScheduledAlerts();

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void end_shouldEndAlert_whenAlertIsActive() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        StateChangeResponse result = service.end(ID, ACTOR);

        assertEquals(AlertStatus.ACTIVE, result.estadoAnterior());
        assertEquals(AlertStatus.ENDED, result.estadoNuevo());
        assertEquals(NOW, alert.getEndedAt());
        verify(stateMachine).assertTransition(AlertStatus.ACTIVE, AlertStatus.ENDED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.END,
                ACTOR,
                AlertStatus.ACTIVE,
                AlertStatus.ENDED,
                "Alerta finalizada");
    }

    @Test
    void end_shouldReturnWithoutChanges_whenAlertIsAlreadyEnded() {
        AlertEntity alert = alertWithStatus(AlertStatus.ENDED);
        alert.setEndedAt(NOW.minusSeconds(10));
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        StateChangeResponse result = service.end(ID, ACTOR);

        assertEquals(AlertStatus.ENDED, result.estadoAnterior());
        assertEquals(AlertStatus.ENDED, result.estadoNuevo());
        assertEquals(NOW.minusSeconds(10), result.fechaCambio());
        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void end_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.end(ID, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void archive_shouldArchiveAlert_whenAlertIsEnded() {
        AlertEntity alert = alertWithStatus(AlertStatus.ENDED);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        StateChangeResponse result = service.archive(ID, ACTOR);

        assertEquals(AlertStatus.ENDED, result.estadoAnterior());
        assertEquals(AlertStatus.ARCHIVED, result.estadoNuevo());
        assertEquals(NOW, alert.getArchivedAt());
        verify(stateMachine).assertTransition(AlertStatus.ENDED, AlertStatus.ARCHIVED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.ARCHIVE,
                ACTOR,
                AlertStatus.ENDED,
                AlertStatus.ARCHIVED,
                "Alerta archivada");
    }

    @Test
    void archive_shouldReturnCurrentState_whenAlertIsAlreadyArchived() {
        AlertEntity alert = alertWithStatus(AlertStatus.ARCHIVED);
        alert.setArchivedAt(NOW.minusSeconds(10));
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        StateChangeResponse result = service.archive(ID, ACTOR);

        assertEquals(AlertStatus.ARCHIVED, result.estadoAnterior());
        assertEquals(AlertStatus.ARCHIVED, result.estadoNuevo());
        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(any());
    }

    private static final class DirectTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
