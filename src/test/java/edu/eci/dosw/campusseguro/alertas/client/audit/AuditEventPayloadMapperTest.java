package edu.eci.dosw.campusseguro.alertas.client.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.AuditRecord;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventPayloadMapperTest {

    private static final UUID ALERT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final UUID AUDIT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    private static final UUID EVENT_ID =
            UUID.fromString("33333333-3333-4333-8333-333333333333");

    private static final UUID USER_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    private static final Instant NOW =
            Instant.parse("2026-11-24T15:00:00Z");

    private final AuditEventPayloadMapper mapper = new AuditEventPayloadMapper();

    @Test
    void toPayload_shouldFillTheContractOfMs08() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.CONFIRM, USER_ID.toString())
                .transition("ACTIVE", "ACTIVE")
                .details("ok")
                .usuarioId(USER_ID.toString())
                .eventoId(EVENT_ID)
                .atributo("destinatarios", 3)
                .build();

        AuditEventPayload payload = mapper.toPayload(AUDIT_ID, record, NOW, "corr-1");

        assertEquals(AUDIT_ID.toString(), payload.id());
        assertEquals("MS03", payload.modulo());
        assertEquals(AuditActions.CONFIRM, payload.accion());
        assertEquals("alerta:" + ALERT_ID, payload.recursoAfectado());
        assertEquals("EXITO", payload.resultado());
        assertEquals(NOW.toString(), payload.fechaHora());
        assertEquals("corr-1", payload.correlationId());
        assertEquals("ok", payload.detalle());
        assertEquals(EVENT_ID.toString(), payload.eventoId());
        assertEquals("SIMULACRO", payload.tipoEvento());
        assertEquals(USER_ID.toString(), payload.usuarioId());
        assertEquals(3, payload.atributos().get("destinatarios"));
    }

    @Test
    void toPayload_shouldTruncateTheDetailToTheLimitOfMs08() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, "admin")
                .details("d".repeat(500))
                .build();

        AuditEventPayload payload = mapper.toPayload(AUDIT_ID, record, NOW, "corr-1");

        assertEquals(100, payload.detalle().length());
    }

    @Test
    void toPayload_shouldOmitTheAttributes_whenThereAreNone() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.ARCHIVE, "admin").build();

        AuditEventPayload payload = mapper.toPayload(AUDIT_ID, record, NOW, "corr-1");

        assertNull(payload.atributos());
    }

    @Test
    void toPayload_shouldSendARealEventWithoutEventId_whenTheAlertIsManual() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.CREATE, "admin").build();

        AuditEventPayload payload = mapper.toPayload(AUDIT_ID, record, NOW, "corr-1");

        assertNull(payload.eventoId());
        assertEquals("REAL", payload.tipoEvento());
    }

    @Test
    void toPayload_shouldSendFailureResult_whenTheFactFailed() {
        AuditRecord record = AuditRecord.builder(
                        ALERT_ID,
                        AuditActions.NOTIFICATION_FAILED,
                        "SYSTEM_NOTIFIER")
                .resultado(edu.eci.dosw.campusseguro.alertas.service.AuditResult.FALLO)
                .build();

        AuditEventPayload payload = mapper.toPayload(AUDIT_ID, record, NOW, "corr-1");

        assertEquals("FALLO", payload.resultado());
        assertNull(payload.usuarioId());
    }
}
