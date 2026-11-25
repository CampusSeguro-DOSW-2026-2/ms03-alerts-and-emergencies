package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditRecordTest {

    private static final UUID ALERT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final UUID EVENT_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void builder_shouldMarkTheFactAsSimulation_whenTheEventIdIsPresent() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.CREATE, "admin")
                .eventoId(EVENT_ID)
                .build();

        assertEquals(EVENT_ID, record.eventoId());
        assertEquals(AuditEventKind.SIMULACRO, record.tipoEvento());
        assertEquals(AuditResult.EXITO, record.resultado());
        assertTrue(record.atributos().isEmpty());
    }

    @Test
    void builder_shouldMarkTheFactAsReal_whenThereIsNoEventId() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.CREATE, "admin")
                .build();

        assertNull(record.eventoId());
        assertEquals(AuditEventKind.REAL, record.tipoEvento());
    }

    @Test
    void builder_shouldDropAttributesWithNullValue() {
        AuditRecord record = AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, "admin")
                .atributo("campos_modificados", 2)
                .atributo("detalle", null)
                .build();

        assertEquals(1, record.atributos().size());
        assertEquals(2, record.atributos().get("campos_modificados"));
    }

    @Test
    void userIdOrNull_shouldReturnTheIdOnlyWhenTheActorIsAUser() {
        assertEquals(
                "33333333-3333-4333-8333-333333333333",
                AuditRecord.userIdOrNull("33333333-3333-4333-8333-333333333333"));
        assertNull(AuditRecord.userIdOrNull("ms06:33333333-3333-4333-8333-333333333333"));
        assertNull(AuditRecord.userIdOrNull("SYSTEM_NOTIFIER"));
        assertNull(AuditRecord.userIdOrNull(null));
    }
}
