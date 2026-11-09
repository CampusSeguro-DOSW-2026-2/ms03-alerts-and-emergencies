package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.event.AlertActivatedEvent;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.ForbiddenOperationException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
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

    private static final RequesterContext ORGANIZER = new RequesterContext(
            "organizer",
            Set.of(UserRole.ORGANIZADOR),
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

        AlertResponse result = service.schedule(ID, scheduleRequest(activationAt), REQUESTER);

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
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void schedule_shouldReschedule_whenAlertIsAlreadyScheduled() {
        AlertEntity alert = alert(AlertStatus.SCHEDULED);
        alert.setActivationAt(NOW.plusSeconds(600));
        AlertResponse expected = responseFor(alert);
        Instant newActivation = NOW.plusSeconds(7200);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.schedule(ID, scheduleRequest(newActivation), REQUESTER);

        assertSame(expected, result);
        assertEquals(AlertStatus.SCHEDULED, alert.getStatus());
        assertEquals(newActivation, alert.getActivationAt());
        verify(stateMachine, never()).assertTransition(any(), any());
        verify(alertRepository).saveAndFlush(alert);
    }

    @Test
    void schedule_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(
                AlertNotFoundException.class,
                () -> service.schedule(ID, scheduleRequest(NOW.plusSeconds(60)), REQUESTER));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void schedule_shouldRejectPastOrEqualActivationDate() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert(AlertStatus.DRAFT)));

        assertThrows(
                InvalidActivationDateException.class,
                () -> service.schedule(ID, scheduleRequest(NOW), REQUESTER));

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
                () -> service.schedule(ID, scheduleRequest(NOW.plusSeconds(60)), REQUESTER));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void schedule_shouldForbidOrganizer_whenAlertIsNotASimulation() {
        AlertEntity alert = alert(AlertStatus.DRAFT);
        alert.setOrigin(AlertOrigin.MANUAL);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.schedule(
                        ID, scheduleRequest(NOW.plusSeconds(60)), ORGANIZER));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stateMachine, auditService);
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

        AlertResponse result = service.activate(ID, REQUESTER, CORRELATION_ID);

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

        service.activate(ID, REQUESTER, CORRELATION_ID);

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

        AlertResponse result = service.activate(ID, REQUESTER, CORRELATION_ID);

        assertSame(expected, result);
        assertEquals(AlertStatus.ACTIVE, alert.getStatus());
        verify(alertRepository).activateIfPending(
                eq(ID), eq(AlertStatus.ACTIVE), eq(NOW), any());
    }

    @Test
    void activate_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.activate(ID, REQUESTER, CORRELATION_ID));

        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void activate_shouldForbidOrganizer_whenAlertIsNotASimulation() {
        AlertEntity alert = alert(AlertStatus.SCHEDULED);
        alert.setOrigin(AlertOrigin.MANUAL);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.activate(ID, ORGANIZER, CORRELATION_ID));

        verify(alertRepository, never()).activateIfPending(any(), any(), any(), any());
        verifyNoInteractions(stateMachine, auditService, eventPublisher);
    }

    @Test
    void activate_shouldBeIdempotent_whenAlertIsAlreadyActive() {
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.activate(ID, REQUESTER, CORRELATION_ID);

        assertSame(expected, result);
        verify(alertRepository, never()).activateIfPending(any(), any(), any(), any());
        verifyNoInteractions(stateMachine, auditService, eventPublisher);
    }

    @Test
    void findDueScheduledAlertIds_shouldReturnIdsOfDueScheduledAlerts() {
        UUID secondId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        AlertEntity first = alert(AlertStatus.SCHEDULED);
        AlertEntity second = alert(AlertStatus.SCHEDULED);
        ReflectionTestUtils.setField(second, "id", secondId);
        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of(first, second));

        assertEquals(List.of(ID, secondId), service.findDueScheduledAlertIds());
        verifyNoInteractions(stateMachine, auditService, eventPublisher);
    }

    @Test
    void findDueScheduledAlertIds_shouldReturnEmpty_whenThereAreNoDueAlerts() {
        when(alertRepository.findByStatusAndActivationAtLessThanEqual(AlertStatus.SCHEDULED, NOW))
                .thenReturn(List.of());

        assertEquals(List.of(), service.findDueScheduledAlertIds());
    }

    @Test
    void archive_shouldArchiveAlert_whenAlertIsActive() {
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.archive(ID, REQUESTER);

        assertSame(expected, result);
        assertEquals(AlertStatus.ARCHIVED, alert.getStatus());
        assertEquals(NOW, alert.getArchivedAt());
        verify(stateMachine).assertTransition(AlertStatus.ACTIVE, AlertStatus.ARCHIVED);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.ARCHIVE,
                ACTOR,
                AlertStatus.ACTIVE,
                AlertStatus.ARCHIVED,
                "Alert archived");
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void archive_shouldForbidOrganizer_whenAlertIsNotASimulation() {
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        alert.setOrigin(AlertOrigin.MANUAL);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        assertThrows(
                ForbiddenOperationException.class,
                () -> service.archive(ID, ORGANIZER));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(stateMachine, auditService);
    }

    @Test
    void archive_shouldAllowOrganizer_whenAlertIsSimulation() {
        AlertEntity alert = alert(AlertStatus.ACTIVE);
        alert.setOrigin(AlertOrigin.SIMULATION);
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.archive(ID, ORGANIZER);

        assertSame(expected, result);
        assertEquals(AlertStatus.ARCHIVED, alert.getStatus());
        verify(auditService).record(
                ID,
                AuditAction.ARCHIVE,
                "organizer",
                AlertStatus.ACTIVE,
                AlertStatus.ARCHIVED,
                "Alert archived");
    }

    @Test
    void archive_shouldBeIdempotent_whenAlertIsAlreadyArchived() {
        AlertEntity alert = alert(AlertStatus.ARCHIVED);
        alert.setArchivedAt(NOW.minusSeconds(10));
        AlertResponse expected = responseFor(alert);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        AlertResponse result = service.archive(ID, REQUESTER);

        assertSame(expected, result);
        assertEquals(NOW.minusSeconds(10), alert.getArchivedAt());
        verifyNoInteractions(stateMachine, auditService, eventPublisher);
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void archive_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.archive(ID, REQUESTER));

        verifyNoInteractions(stateMachine, auditService);
    }
}
