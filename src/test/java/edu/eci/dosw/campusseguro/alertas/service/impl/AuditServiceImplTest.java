package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
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

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private AuditEventRepository auditEventRepository;

    private AuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AuditEventEntity captureSaved() {
        ArgumentCaptor<AuditEventEntity> captor = ArgumentCaptor.forClass(AuditEventEntity.class);
        verify(auditEventRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void record_shouldSaveEventWithAllFields() {
        service.record(ALERT_ID, AuditAction.SCHEDULE, "admin", AlertStatus.DRAFT, AlertStatus.SCHEDULED, "detalle");

        AuditEventEntity event = captureSaved();
        assertEquals(ALERT_ID, event.getAlertId());
        assertEquals(AuditAction.SCHEDULE, event.getAction());
        assertEquals("admin", event.getActor());
        assertEquals(AlertStatus.DRAFT, event.getFromStatus());
        assertEquals(AlertStatus.SCHEDULED, event.getToStatus());
        assertEquals("detalle", event.getDetails());
        assertEquals(NOW, event.getCreatedAt());
        assertEquals(NOW, event.getUpdatedAt());
    }

    @Test
    void record_shouldSaveEvent_whenFromStatusAndDetailsAreNull() {
        service.record(ALERT_ID, AuditAction.CREATE, "admin", null, AlertStatus.DRAFT, null);

        AuditEventEntity event = captureSaved();
        assertNull(event.getFromStatus());
        assertEquals(AlertStatus.DRAFT, event.getToStatus());
        assertNull(event.getDetails());
    }

    @Test
    void record_shouldTruncateActor_whenLongerThan64Chars() {
        String actor = "a".repeat(100);

        service.record(ALERT_ID, AuditAction.UPDATE, actor, AlertStatus.DRAFT, AlertStatus.DRAFT, "x");

        assertEquals(64, captureSaved().getActor().length());
    }

    @Test
    void record_shouldNotTruncateActor_whenExactly64Chars() {
        String actor = "a".repeat(64);

        service.record(ALERT_ID, AuditAction.UPDATE, actor, AlertStatus.DRAFT, AlertStatus.DRAFT, "x");

        assertEquals(actor, captureSaved().getActor());
    }

    @Test
    void record_shouldTruncateDetails_whenLongerThan500Chars() {
        String details = "d".repeat(800);

        service.record(ALERT_ID, AuditAction.UPDATE, "admin", AlertStatus.DRAFT, AlertStatus.DRAFT, details);

        assertEquals(500, captureSaved().getDetails().length());
    }

    @Test
    void record_shouldNotTruncateDetails_whenExactly500Chars() {
        String details = "d".repeat(500);

        service.record(ALERT_ID, AuditAction.UPDATE, "admin", AlertStatus.DRAFT, AlertStatus.DRAFT, details);

        assertEquals(details, captureSaved().getDetails());
    }

    @Test
    void record_shouldRequireExistingTransaction() throws NoSuchMethodException {
        Method method = AuditServiceImpl.class.getMethod(
                "record",
                UUID.class,
                AuditAction.class,
                String.class,
                AlertStatus.class,
                AlertStatus.class,
                String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertEquals(Propagation.MANDATORY, transactional.propagation());
    }
}