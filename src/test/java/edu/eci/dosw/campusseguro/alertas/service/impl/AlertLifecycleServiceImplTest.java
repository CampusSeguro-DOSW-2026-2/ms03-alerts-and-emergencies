package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
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
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import edu.eci.dosw.campusseguro.alertas.security.UserRole;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateMachine;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertLifecycleServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ACTOR = "admin";

    private static final String CORRELATION_ID =
            "11111111-1111-4111-8111-111111111111";

    private static final RequesterContext REQUESTER = new RequesterContext(
            ACTOR,
            Set.of(UserRole.COORDINADOR),
            null);

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertScopeItemRepository scopeItemRepository;

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private AlertStateMachine stateMachine;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AlertLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertLifecycleServiceImpl(
                alertRepository,
                scopeItemRepository,
                recipientRepository,
                alertMapper,
                auditService,
                stateMachine,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher);
    }

    private AlertEntity alert(AlertStatus status) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setStatus(status);
        return alert;
    }

    private AlertResponse responseFor(AlertEntity alert) {
        AlertResponse response = org.mockito.Mockito.mock(AlertResponse.class);
        when(scopeItemRepository.findByAlertId(ID)).thenReturn(List.of());
        when(recipientRepository.findByAlertId(ID)).thenReturn(List.of());
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(response);
        return response;
    }

    private AlertScheduleRequest scheduleRequest(Instant activationAt) {
        return new AlertScheduleRequest(activationAt);
    }

    @Test
    void schedule_shouldScheduleAlert_whenAlertIsDraftAndDateIsInTheFuture() {
        AlertEntity alert = alert(AlertStatus.DRAFT);
        AlertResponse expected = responseFor(alert);
        Instant activationAt = NOW.plusSeconds(3600);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.schedule(ID, scheduleRequest(activationAt), ACTOR);

        assertSame(expected, result);
        assertEquals(AlertStatus.SCHEDULED, alert.getStatus());
        assertEquals(activationAt, alert.getActivationAt());
        assertEquals(NOW, alert.getUpdatedAt());
        verify(stateMachine).assertTransition(AlertStatus.DRAFT, AlertStatus.SCHEDULED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.SCHEDULE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.SCHEDULED,
                "Alert scheduled for " + activationAt);
    }

    @Test
    void schedule_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(
                AlertNotFoundException.class,
                () -> service.schedule(ID, scheduleRequest(NOW.plusSeconds(60)), ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldRejectPastOrEqualActivationDate() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert(AlertStatus.DRAFT)));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, scheduleRequest(NOW), ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldNotSaveNorAudit_whenTransitionIsInvalid() {
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        doThrow(new InvalidTransitionException(
                AlertStatus.ACTIVE, AlertStatus.SCHEDULED))
                .when(stateMachine)
                .assertTransition(AlertStatus.ACTIVE, AlertStatus.SCHEDULED);

        assertThrows(
                InvalidTransitionException.class,
                () -> service.schedule(ID, scheduleRequest(NOW.plusSeconds(60)), ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void activate_shouldActivateAlert_whenAlertIsScheduled() {
        AlertEntity alert = alert(AlertStatus.SCHEDULED);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertRepository.activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any()))
                .thenAnswer(invocation -> {
                    alert.setStatus(AlertStatus.ACTIVE);
                    alert.setActivatedAt(NOW);
                    return 1;
                });

        AlertResponse result = service.activate(ID, ACTOR, CORRELATION_ID);

        assertSame(expected, result);
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        assertEquals(NOW, alert.getActivatedAt());
        verify(stateMachine).assertTransition(AlertStatus.SCHEDULED, AlertStatus.ACTIVE);
        verify(alertRepository).activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any());
        verify(alertRepository, never()).saveAndFlush(any());
        verify(auditService).record(
                ID,
                AuditAction.ACTIVATE,
                ACTOR,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alert activated from SCHEDULED");
        verify(eventPublisher).publishEvent(
                new AlertActivatedEvent(ID, AlertStatus.SCHEDULED, NOW, CORRELATION_ID));
    }

    @Test
    void activate_shouldActivateAlert_whenAlertIsDraft() {
        AlertEntity alert = alert(AlertStatus.DRAFT);
        responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertRepository.activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any()))
                .thenAnswer(invocation -> {
                    alert.setStatus(AlertStatus.ACTIVE);
                    alert.setActivatedAt(NOW);
                    return 1;
                });

        service.activate(ID, ACTOR, CORRELATION_ID);

        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        verify(stateMachine).assertTransition(AlertStatus.DRAFT, AlertStatus.ACTIVE);
        verify(alertRepository).activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any());
        verify(eventPublisher).publishEvent(
                new AlertActivatedEvent(ID, AlertStatus.DRAFT, NOW, CORRELATION_ID));
    }

    @Test
    void activate_shouldAllowScheduledAlertBeforeItsActivationDate() {
        AlertEntity alert = alert(AlertStatus.SCHEDULED);
        alert.setActivationAt(NOW.plusSeconds(60));
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertRepository.activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any()))
                .thenAnswer(invocation -> {
                    alert.setStatus(AlertStatus.ACTIVE);
                    alert.setActivatedAt(NOW);
                    return 1;
                });

        AlertResponse result = service.activate(ID, ACTOR, CORRELATION_ID);

        assertSame(expected, result);
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        verify(alertRepository).activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any());
    }

    @Test
    void activate_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.activate(ID, ACTOR, CORRELATION_ID));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void activateScheduledAlerts_shouldActivateDueAlerts() {
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AlertEntity first = alert(AlertStatus.SCHEDULED);
        AlertEntity second = alert(AlertStatus.SCHEDULED);
        ReflectionTestUtils.setField(second, "id", secondId);

        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of(first, second));
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(first));
        when(alertRepository.findForUpdate(secondId)).thenReturn(Optional.of(second));
        when(alertRepository.activateIfPending(
                any(UUID.class), eq(AlertStatus.ACTIVE), eq(NOW), any()))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0);
                    AlertEntity target = ID.equals(id) ? first : second;
                    target.setStatus(AlertStatus.ACTIVE);
                    target.setActivatedAt(NOW);
                    return 1;
                });

        service.activateScheduledAlerts();

        assertEquals(AlertStatus.ACTIVE, first.getStatus());
        assertEquals(AlertStatus.ACTIVE, second.getStatus());
        verify(alertRepository, never()).saveAndFlush(any(AlertEntity.class));
        verify(auditService).record(
                ID,
                AuditAction.ACTIVATE,
                ActorResolver.SYSTEM_SCHEDULER,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alert activated from SCHEDULED");
        verify(auditService).record(
                secondId,
                AuditAction.ACTIVATE,
                ActorResolver.SYSTEM_SCHEDULER,
                AlertStatus.SCHEDULED,
                AlertStatus.ACTIVE,
                "Alert activated from SCHEDULED");
        org.mockito.ArgumentCaptor<Object> events =
                org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, org.mockito.Mockito.times(2))
                .publishEvent(events.capture());

        assertTrue(events.getAllValues().stream()
                .filter(AlertActivatedEvent.class::isInstance)
                .map(AlertActivatedEvent.class::cast)
                .anyMatch(event -> ID.equals(event.alertId())
                        && event.previousStatus() == AlertStatus.SCHEDULED
                        && NOW.equals(event.activatedAt())
                        && event.correlationId() != null));
        assertTrue(events.getAllValues().stream()
                .filter(AlertActivatedEvent.class::isInstance)
                .map(AlertActivatedEvent.class::cast)
                .anyMatch(event -> secondId.equals(event.alertId())
                        && event.previousStatus() == AlertStatus.SCHEDULED
                        && NOW.equals(event.activatedAt())
                        && event.correlationId() != null));
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
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.end(ID, new AlertEndRequest(null), REQUESTER);

        assertSame(expected, result);
        assertEquals(AlertStatus.ENDED, alert.getStatus());
        assertEquals(NOW, alert.getEndedAt());
        verify(stateMachine).assertTransition(AlertStatus.ACTIVE, AlertStatus.ENDED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.END,
                ACTOR,
                AlertStatus.ACTIVE,
                AlertStatus.ENDED,
                "Alert ended");
    }

    @Test
    void end_shouldReturnWithoutChanges_whenAlertIsAlreadyEndedOrArchived() {
        AlertEntity ended = alert(AlertStatus.ENDED);
        ended.setEndedAt(NOW.minusSeconds(10));
        AlertResponse expected = responseFor(ended);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(ended));

        AlertResponse result = service.end(ID, null, REQUESTER);

        assertSame(expected, result);
        verifyNoInteractions(stateMachine, auditService);
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void end_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.end(ID, null, REQUESTER));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void archive_shouldArchiveAlert_whenAlertIsEnded() {
        AlertEntity alert = alert(AlertStatus.ENDED);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.archive(ID, ACTOR);

        assertSame(expected, result);
        assertEquals(AlertStatus.ARCHIVED, alert.getStatus());
        assertEquals(NOW, alert.getArchivedAt());
        verify(stateMachine).assertTransition(AlertStatus.ENDED, AlertStatus.ARCHIVED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.ARCHIVE,
                ACTOR,
                AlertStatus.ENDED,
                AlertStatus.ARCHIVED,
                "Alert archived");
    }

    @Test
    void archive_shouldRejectAlreadyArchivedAlert() {
        AlertEntity alert = alert(AlertStatus.ARCHIVED);
        alert.setArchivedAt(NOW.minusSeconds(10));
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        doThrow(new InvalidTransitionException(
                AlertStatus.ARCHIVED, AlertStatus.ARCHIVED))
                .when(stateMachine)
                .assertTransition(AlertStatus.ARCHIVED, AlertStatus.ARCHIVED);

        assertThrows(
                InvalidTransitionException.class,
                () -> service.archive(ID, ACTOR));

        verifyNoInteractions(auditService);
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void archive_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.archive(ID, ACTOR));

        verifyNoInteractions(stateMachine, auditService);
    }
}
