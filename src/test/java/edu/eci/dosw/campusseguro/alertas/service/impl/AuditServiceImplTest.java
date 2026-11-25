package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayload;
import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayloadMapper;
import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.AuditOutboxService;
import edu.eci.dosw.campusseguro.alertas.service.AuditRecord;
import edu.eci.dosw.campusseguro.alertas.service.AuditResult;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuditServiceImplTest {

    private static final Instant NOW =
            Instant.parse("2026-10-15T18:00:00Z");

    private static final UUID ALERT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID EVENT_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SIMULATION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AuditOutboxService auditOutboxService;

    private final AuditEventPayloadMapper payloadMapper =
            new AuditEventPayloadMapper();

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(
                auditEventRepository,
                auditOutboxService,
                payloadMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** Imita a Hibernate: el id se asigna al guardar. */
    private void stubSavedEvent() {
        when(auditEventRepository.save(any())).thenAnswer(invocation -> {
            AuditEventEntity entity = invocation.getArgument(0);
            entity.setId(EVENT_ID);
            return entity;
        });
    }

    private AuditEventEntity captureSaved() {
        ArgumentCaptor<AuditEventEntity> captor =
                ArgumentCaptor.forClass(AuditEventEntity.class);

        verify(auditEventRepository).save(captor.capture());

        return captor.getValue();
    }

    @Test
    void record_shouldSaveEventWithAllFields() {
        stubSavedEvent();

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.SCHEDULE, "admin")
                .transition("DRAFT", "SCHEDULED")
                .details("detalle")
                .build());

        AuditEventEntity event = captureSaved();

        assertEquals(ALERT_ID, event.getAlertId());
        assertEquals(AuditActions.SCHEDULE, event.getAction());
        assertEquals("admin", event.getActor());
        assertEquals("DRAFT", event.getFromStatus());
        assertEquals("SCHEDULED", event.getToStatus());
        assertEquals("detalle", event.getDetails());
        assertEquals(NOW, event.getCreatedAt());
        assertEquals(NOW, event.getUpdatedAt());
    }

    @Test
    void record_shouldSaveEvent_whenFromStatusAndDetailsAreNull() {
        stubSavedEvent();

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.CREATE, "admin")
                .transition(null, "DRAFT")
                .build());

        AuditEventEntity event = captureSaved();

        assertNull(event.getFromStatus());
        assertEquals("DRAFT", event.getToStatus());
        assertNull(event.getDetails());
    }

    @Test
    void record_shouldRejectLegacyEndAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(AuditRecord.builder(
                        ALERT_ID,
                        AuditActions.END,
                        "admin").build()));
    }

    @Test
    void record_shouldTruncateActor_whenLongerThan64Chars() {
        stubSavedEvent();

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, "a".repeat(100))
                .transition("DRAFT", "DRAFT")
                .details("x")
                .build());

        assertEquals(64, captureSaved().getActor().length());
    }

    @Test
    void record_shouldNotTruncateActor_whenExactly64Chars() {
        stubSavedEvent();
        String actor = "a".repeat(64);

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, actor)
                .transition("DRAFT", "DRAFT")
                .details("x")
                .build());

        assertEquals(actor, captureSaved().getActor());
    }

    @Test
    void record_shouldTruncateDetails_whenLongerThan500Chars() {
        stubSavedEvent();

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, "admin")
                .transition("DRAFT", "DRAFT")
                .details("d".repeat(800))
                .build());

        assertEquals(500, captureSaved().getDetails().length());
    }

    @Test
    void record_shouldNotTruncateDetails_whenExactly500Chars() {
        stubSavedEvent();
        String details = "d".repeat(500);

        service.record(AuditRecord.builder(ALERT_ID, AuditActions.UPDATE, "admin")
                .transition("DRAFT", "DRAFT")
                .details(details)
                .build());

        assertEquals(details, captureSaved().getDetails());
    }

    @Test
    void record_shouldEnqueueTheContractPayload() {
        stubSavedEvent();

        service.record(AuditRecord.builder(
                        ALERT_ID,
                        AuditActions.NOTIFICATION_FAILED,
                        "SYSTEM_NOTIFIER")
                .transition("ACTIVE", "ACTIVE")
                .details("d".repeat(200))
                .eventoId(SIMULATION_ID)
                .resultado(AuditResult.FALLO)
                .atributo("destinatarios", 3)
                .build());

        ArgumentCaptor<AuditEventPayload> captor =
                ArgumentCaptor.forClass(AuditEventPayload.class);

        verify(auditOutboxService).enqueue(captor.capture());

        AuditEventPayload payload = captor.getValue();

        assertEquals(EVENT_ID.toString(), payload.id());
        assertEquals("MS03", payload.modulo());
        assertEquals(AuditActions.NOTIFICATION_FAILED, payload.accion());
        assertEquals("alerta:" + ALERT_ID, payload.recursoAfectado());
        assertEquals("FALLO", payload.resultado());
        assertEquals("SIMULACRO", payload.tipoEvento());
        assertEquals(SIMULATION_ID.toString(), payload.eventoId());
        assertEquals(100, payload.detalle().length());
        assertNull(payload.usuarioId());
        assertEquals(3, payload.atributos().get("destinatarios"));
        assertEquals(NOW.toString(), payload.fechaHora());
    }

    @Test
    void record_shouldRequireAnExistingTransaction()
            throws NoSuchMethodException {

        Method method = AuditServiceImpl.class.getMethod(
                "record",
                AuditRecord.class);

        Transactional transactional =
                method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }
}
