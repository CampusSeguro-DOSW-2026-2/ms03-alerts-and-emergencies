package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "admin";

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertScopeItemRepository scopeItemRepository;

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private AlertStateMachine stateMachine;

    @Mock
    private AuditService auditService;

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlertLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AlertLifecycleServiceImpl(
                alertRepository,
                scopeItemRepository,
                recipientRepository,
                stateMachine,
                auditService,
                alertMapper,
                fixedClock,
                eventPublisher);
    }

    // helpers 

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

    private void givenAlertExists(AlertEntity alert) {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
    }

    private AlertResponse givenMapperReturnsResponse(AlertEntity alert) {
        AlertResponse response = mock(AlertResponse.class);
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(response);
        return response;
    }

    private AlertScheduleRequest requestWithActivation(Instant activationAt) {
        AlertScheduleRequest request = mock(AlertScheduleRequest.class);
        when(request.activationAt()).thenReturn(activationAt);
        return request;
    }

    // schedule

    @Test
    void schedule_shouldScheduleAlert_whenAlertIsDraftAndDateIsInTheFuture() {
        AlertEntity alert = alertWithStatus(AlertStatus.DRAFT);
        Instant activationAt = NOW.plus(1, ChronoUnit.HOURS);
        givenAlertExists(alert);
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.schedule(ID, requestWithActivation(activationAt), ACTOR);

        assertSame(expected, result);
        assertEquals(AlertStatus.SCHEDULED, alert.getStatus());
        assertEquals(activationAt, alert.getActivationAt());
        assertEquals(NOW, alert.getUpdatedAt());

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
        AlertScheduleRequest request = mock(AlertScheduleRequest.class);

        assertThrows(AlertNotFoundException.class, () -> service.schedule(ID, request, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedule_shouldThrowInvalidActivationDate_whenDateIsNull() {
        givenAlertExists(alertWithStatus(AlertStatus.DRAFT));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, requestWithActivation(null), ACTOR));

        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedule_shouldThrowInvalidActivationDate_whenDateIsInThePast() {
        givenAlertExists(alertWithStatus(AlertStatus.DRAFT));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, requestWithActivation(NOW.minusSeconds(1)), ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldThrowInvalidActivationDate_whenDateEqualsCurrentTime() {
        givenAlertExists(alertWithStatus(AlertStatus.DRAFT));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, requestWithActivation(NOW), ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldNotSaveNorAudit_whenTransitionIsInvalid() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        givenAlertExists(alert);
        doThrow(new InvalidTransitionException(AlertStatus.ACTIVE, AlertStatus.SCHEDULED))
                .when(stateMachine)
                .assertTransition(AlertStatus.ACTIVE, AlertStatus.SCHEDULED);

        assertThrows(
                InvalidTransitionException.class,
                () -> service.schedule(ID, requestWithActivation(NOW.plusSeconds(60)), ACTOR));

        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditService);
    }

    // activate

    @Test
    void activate_shouldActivateAlert_whenAlertIsScheduled() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.SCHEDULED));
        when(alertRepository.activateIfPending(
                        eq(ID),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alert));
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.activate(ID, ACTOR);

        assertSame(expected, result);
        verify(stateMachine).assertTransition(AlertStatus.SCHEDULED, AlertStatus.ACTIVE);
        verify(alertRepository).activateIfPending(
                ID,
                NOW,
                AlertStatus.ACTIVE,
                List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED));
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
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.DRAFT));
        when(alertRepository.activateIfPending(
                        eq(ID),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alert));
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.activate(ID, ACTOR);

        assertSame(expected, result);
        verify(stateMachine).assertTransition(AlertStatus.DRAFT, AlertStatus.ACTIVE);
        verify(auditService).record(
                ID,
                AuditAction.ACTIVATE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.ACTIVE,
                "Alerta activada");
    }

    @Test
    void activate_shouldReturnAlertWithoutAudit_whenAlertIsAlreadyActive() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.ACTIVE));
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alert));
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.activate(ID, ACTOR);

        assertSame(expected, result);
        verifyNoInteractions(stateMachine, auditService);
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

    // activateScheduledAlerts

    @Test
    void activateScheduledAlerts_shouldActivateAllAlerts_whenTheyAreDue() {
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AlertEntity first = alertWithStatus(AlertStatus.SCHEDULED, ID);
        AlertEntity second = alertWithStatus(AlertStatus.SCHEDULED, secondId);
        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of(first, second));
        when(alertRepository.findStatusById(ID)).thenReturn(Optional.of(AlertStatus.SCHEDULED));
        when(alertRepository.findStatusById(secondId)).thenReturn(Optional.of(AlertStatus.SCHEDULED));
        when(alertRepository.activateIfPending(
                        eq(ID),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);
        when(alertRepository.activateIfPending(
                        eq(secondId),
                        eq(NOW),
                        eq(AlertStatus.ACTIVE),
                        eq(List.of(AlertStatus.DRAFT, AlertStatus.SCHEDULED))))
                .thenReturn(1);
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alertWithStatus(AlertStatus.ACTIVE, ID)));
        when(alertRepository.findById(secondId))
                .thenReturn(Optional.of(alertWithStatus(AlertStatus.ACTIVE, secondId)));

        service.activateScheduledAlerts();

        verify(stateMachine, times(2)).assertTransition(AlertStatus.SCHEDULED, AlertStatus.ACTIVE);
        verify(auditService).record(
                ID,
                AuditAction.ACTIVATE,
                ActorResolver.SYSTEM_SCHEDULER,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alerta activada automáticamente");
        verify(auditService).record(
                secondId,
                AuditAction.ACTIVATE,
                ActorResolver.SYSTEM_SCHEDULER,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alerta activada automáticamente");
        verify(eventPublisher, times(2)).publishEvent(any(AlertActivatedEvent.class));
    }

    @Test
    void activateScheduledAlerts_shouldNotActivateAnything_whenThereAreNoDueAlerts() {
        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of());

        service.activateScheduledAlerts();

        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    // end

    @Test
    void end_shouldEndAlert_whenAlertIsActive() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        givenAlertExists(alert);
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.end(ID, ACTOR);

        assertSame(expected, result);
        assertEquals(AlertStatus.ENDED, alert.getStatus());
        assertEquals(NOW, alert.getEndedAt());
        assertEquals(NOW, alert.getUpdatedAt());
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
        givenAlertExists(alert);
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.end(ID, ACTOR);

        assertSame(expected, result);
        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void end_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.end(ID, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void end_shouldNotSaveNorAudit_whenTransitionIsInvalid() {
        AlertEntity alert = alertWithStatus(AlertStatus.DRAFT);
        givenAlertExists(alert);
        doThrow(new InvalidTransitionException(AlertStatus.DRAFT, AlertStatus.ENDED))
                .when(stateMachine)
                .assertTransition(AlertStatus.DRAFT, AlertStatus.ENDED);

        assertThrows(InvalidTransitionException.class, () -> service.end(ID, ACTOR));

        assertEquals(AlertStatus.DRAFT, alert.getStatus());
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditService);
    }

    // archive

    @Test
    void archive_shouldArchiveAlert_whenAlertIsEnded() {
        AlertEntity alert = alertWithStatus(AlertStatus.ENDED);
        givenAlertExists(alert);
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.archive(ID, ACTOR);

        assertSame(expected, result);
        assertEquals(AlertStatus.ARCHIVED, alert.getStatus());
        assertEquals(NOW, alert.getArchivedAt());
        assertEquals(NOW, alert.getUpdatedAt());
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
    void archive_shouldReturnWithoutChanges_whenAlertIsAlreadyArchived() {
        AlertEntity alert = alertWithStatus(AlertStatus.ARCHIVED);
        givenAlertExists(alert);
        AlertResponse expected = givenMapperReturnsResponse(alert);

        AlertResponse result = service.archive(ID, ACTOR);

        assertSame(expected, result);
        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void archive_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.archive(ID, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void archive_shouldNotSaveNorAudit_whenTransitionIsInvalid() {
        AlertEntity alert = alertWithStatus(AlertStatus.ACTIVE);
        givenAlertExists(alert);
        doThrow(new InvalidTransitionException(AlertStatus.ACTIVE, AlertStatus.ARCHIVED))
                .when(stateMachine)
                .assertTransition(AlertStatus.ACTIVE, AlertStatus.ARCHIVED);

        assertThrows(InvalidTransitionException.class, () -> service.archive(ID, ACTOR));

        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        verify(alertRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(auditService);
    }
}