package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayload;
import edu.eci.dosw.campusseguro.alertas.entity.AuditOutboxEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AuditOutboxRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditOutboxMessage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditOutboxServiceImplTest {

    private static final Instant NOW =
            Instant.parse("2026-11-24T15:00:00Z");

    private static final UUID OUTBOX_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final long STUCK_TIMEOUT_MS = 60_000;

    @Mock
    private AuditOutboxRepository repository;

    private AuditOutboxServiceImpl service(boolean enabled) {
        return new AuditOutboxServiceImpl(
                repository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabled,
                50,
                STUCK_TIMEOUT_MS);
    }

    private AuditEventPayload payload() {
        return new AuditEventPayload(
                OUTBOX_ID.toString(),
                null,
                "MS03",
                "ACTIVATE",
                "alerta:" + OUTBOX_ID,
                "EXITO",
                NOW.toString(),
                "corr-1",
                "ok",
                null,
                "REAL",
                null);
    }

    private AuditOutboxEntity pending() {
        AuditOutboxEntity entity = new AuditOutboxEntity();
        entity.setId(OUTBOX_ID);
        entity.setPayload("{\"modulo\":\"MS03\"}");
        entity.setEstado(AuditOutboxServiceImpl.PENDIENTE);
        entity.setIntentos(0);
        entity.setProximoIntento(NOW);
        entity.setUpdatedAt(NOW);
        return entity;
    }

    @Test
    void enqueue_shouldStoreThePayloadAsPendingWithTheContractJson() {
        service(true).enqueue(payload());

        ArgumentCaptor<AuditOutboxEntity> captor =
                ArgumentCaptor.forClass(AuditOutboxEntity.class);
        verify(repository).save(captor.capture());
        AuditOutboxEntity saved = captor.getValue();

        assertEquals(OUTBOX_ID, saved.getId());
        assertEquals(AuditOutboxServiceImpl.PENDIENTE, saved.getEstado());
        assertEquals(0, saved.getIntentos());
        assertEquals(NOW, saved.getProximoIntento());
        assertTrue(saved.getPayload().contains("\"modulo\":\"MS03\""));
        assertTrue(saved.getPayload().contains("\"correlation_id\":\"corr-1\""));
        assertFalse(saved.getPayload().contains("usuario_id"));
    }

    @Test
    void enqueue_shouldDoNothing_whenTheIntegrationIsDisabled() {
        service(false).enqueue(payload());

        verifyNoInteractions(repository);
    }

    @Test
    void claimDue_shouldMarkTheBatchAsInFlightAndCountTheAttempt() {
        AuditOutboxEntity entity = pending();
        when(repository.findDue(eq(AuditOutboxServiceImpl.PENDIENTE), eq(NOW), any()))
                .thenReturn(List.of(entity));

        List<AuditOutboxMessage> batch = service(true).claimDue();

        assertEquals(1, batch.size());
        assertEquals(OUTBOX_ID, batch.get(0).id());
        assertEquals(1, batch.get(0).intentos());
        assertEquals(AuditOutboxServiceImpl.ENVIANDO, entity.getEstado());
        assertEquals(1, entity.getIntentos());
    }

    @Test
    void claimDue_shouldReturnTheStuckBatchToPending() {
        AuditOutboxEntity stuck = pending();
        stuck.setEstado(AuditOutboxServiceImpl.ENVIANDO);
        when(repository.findStuck(
                eq(AuditOutboxServiceImpl.ENVIANDO),
                eq(NOW.minusMillis(STUCK_TIMEOUT_MS)),
                any())).thenReturn(List.of(stuck));

        service(true).claimDue();

        assertEquals(AuditOutboxServiceImpl.PENDIENTE, stuck.getEstado());
    }

    @Test
    void markSent_shouldClearTheErrorAndRecordTheDeliveryTime() {
        AuditOutboxEntity entity = pending();
        entity.setUltimoError("timeout");
        when(repository.findById(OUTBOX_ID)).thenReturn(Optional.of(entity));

        service(true).markSent(OUTBOX_ID);

        assertEquals(AuditOutboxServiceImpl.ENVIADO, entity.getEstado());
        assertEquals(NOW, entity.getEnviadoEn());
        assertNull(entity.getUltimoError());
    }

    @Test
    void markRetry_shouldRescheduleAndKeepTheError() {
        AuditOutboxEntity entity = pending();
        when(repository.findById(OUTBOX_ID)).thenReturn(Optional.of(entity));

        service(true).markRetry(OUTBOX_ID, "MS08 respondio 503", NOW.plusSeconds(5));

        assertEquals(AuditOutboxServiceImpl.PENDIENTE, entity.getEstado());
        assertEquals(NOW.plusSeconds(5), entity.getProximoIntento());
        assertEquals("MS08 respondio 503", entity.getUltimoError());
    }

    @Test
    void markDiscarded_shouldKeepTheReason() {
        AuditOutboxEntity entity = pending();
        when(repository.findById(OUTBOX_ID)).thenReturn(Optional.of(entity));

        service(true).markDiscarded(OUTBOX_ID, "MS08 rechazo la auditoria con estado 400");

        assertEquals(AuditOutboxServiceImpl.DESCARTADO, entity.getEstado());
        assertEquals("MS08 rechazo la auditoria con estado 400", entity.getUltimoError());
    }

    @Test
    void markSent_shouldIgnoreAnUnknownFact() {
        when(repository.findById(OUTBOX_ID)).thenReturn(Optional.empty());

        service(true).markSent(OUTBOX_ID);

        verify(repository).findById(OUTBOX_ID);
    }
}
