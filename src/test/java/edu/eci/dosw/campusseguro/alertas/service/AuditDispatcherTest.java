package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.audit.AuditClient;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditDeliveryException;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditRejectedException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditDispatcherTest {

    private static final Instant NOW =
            Instant.parse("2026-11-24T15:00:00Z");

    private static final UUID OUTBOX_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final String PAYLOAD = "{\"modulo\":\"MS03\"}";

    @Mock
    private AuditOutboxService outboxService;

    @Mock
    private AuditClient auditClient;

    private AuditDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AuditDispatcher(
                outboxService,
                auditClient,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                1000,
                8000);
    }

    @Test
    void dispatch_shouldMarkTheFactAsSent_whenMs08Accepts() {
        when(outboxService.claimDue())
                .thenReturn(List.of(new AuditOutboxMessage(OUTBOX_ID, PAYLOAD, 1)));

        assertEquals(1, dispatcher.dispatch());

        verify(auditClient).send(PAYLOAD);
        verify(outboxService).markSent(OUTBOX_ID);
        verify(outboxService, never()).markRetry(any(), any(), any());
        verify(outboxService, never()).markDiscarded(any(), any());
    }

    @Test
    void dispatch_shouldDiscardTheFact_whenMs08RejectsIt() {
        when(outboxService.claimDue())
                .thenReturn(List.of(new AuditOutboxMessage(OUTBOX_ID, PAYLOAD, 1)));
        doThrow(new AuditRejectedException(400)).when(auditClient).send(PAYLOAD);

        dispatcher.dispatch();

        verify(outboxService).markDiscarded(
                OUTBOX_ID, "MS08 rechazo la auditoria con estado 400");
        verify(outboxService, never()).markRetry(any(), any(), any());
    }

    @Test
    void dispatch_shouldRescheduleWithBackoff_whenTheDeliveryFails() {
        when(outboxService.claimDue())
                .thenReturn(List.of(new AuditOutboxMessage(OUTBOX_ID, PAYLOAD, 2)));
        doThrow(new AuditDeliveryException("MS08 respondio 503", null))
                .when(auditClient).send(PAYLOAD);

        dispatcher.dispatch();

        verify(outboxService).markRetry(OUTBOX_ID, "MS08 respondio 503", NOW.plusMillis(2000));
        verify(outboxService, never()).markSent(any());
    }

    @Test
    void dispatch_shouldDiscardTheFact_whenThereAreNoAttemptsLeft() {
        when(outboxService.claimDue())
                .thenReturn(List.of(new AuditOutboxMessage(OUTBOX_ID, PAYLOAD, 3)));
        doThrow(new AuditDeliveryException("sin red", null)).when(auditClient).send(PAYLOAD);

        dispatcher.dispatch();

        verify(outboxService).markDiscarded(OUTBOX_ID, "sin red");
        verify(outboxService, never()).markRetry(any(), any(), any());
    }

    @Test
    void dispatch_shouldNotSendAnything_whenThereIsNothingPending() {
        when(outboxService.claimDue()).thenReturn(List.of());

        assertEquals(0, dispatcher.dispatch());

        verify(auditClient, never()).send(any());
    }

    @Test
    void backoff_shouldGrowExponentiallyAndStopAtTheCeiling() {
        assertEquals(1000, dispatcher.backoffMillis(1));
        assertEquals(2000, dispatcher.backoffMillis(2));
        assertEquals(4000, dispatcher.backoffMillis(3));
        assertEquals(8000, dispatcher.backoffMillis(4));
        assertEquals(8000, dispatcher.backoffMillis(25));
    }
}
