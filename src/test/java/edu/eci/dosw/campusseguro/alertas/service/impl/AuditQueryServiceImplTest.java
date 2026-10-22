package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceImplTest {

    private static final UUID ALERT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AlertRepository alertRepository;

    private AuditQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditQueryServiceImpl(auditEventRepository, alertRepository);
    }

    private AuditEventEntity event(String actor) {
        AuditEventEntity event = new AuditEventEntity();
        ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
        event.setAlertId(ALERT_ID);
        event.setActor(actor);
        event.setAction(AuditAction.CREATE);
        event.setFromStatus(null);
        event.setToStatus(AlertStatus.DRAFT);
        event.setDetails("detalle");
        event.setCreatedAt(NOW);
        return event;
    }

    @Test
    void findByAlert_shouldReturnOrderedEventsWithActorType() {
        when(alertRepository.existsById(ALERT_ID)).thenReturn(true);
        when(auditEventRepository.findByAlertIdOrderByCreatedAtAsc(ALERT_ID)).thenReturn(List.of(
                event("usr-001"),
                event(ActorResolver.SYSTEM_SCHEDULER),
                event(Role.SERVICE_MS06.name())));

        List<AuditEventResponse> events = service.findByAlert(ALERT_ID);

        assertEquals(3, events.size());
        assertEquals("USER", events.get(0).actorType());
        assertEquals("SYSTEM", events.get(1).actorType());
        assertEquals("SERVICE", events.get(2).actorType());
        assertEquals(AlertStatus.DRAFT, events.get(0).toStatus());
        assertEquals(NOW, events.get(0).occurredAt());
        verify(auditEventRepository).findByAlertIdOrderByCreatedAtAsc(ALERT_ID);
    }

    @Test
    void findByAlert_shouldReturnEmptyList_whenThereAreNoEvents() {
        when(alertRepository.existsById(ALERT_ID)).thenReturn(true);
        when(auditEventRepository.findByAlertIdOrderByCreatedAtAsc(ALERT_ID)).thenReturn(List.of());

        assertTrue(service.findByAlert(ALERT_ID).isEmpty());
    }

    @Test
    void findByAlert_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.existsById(ALERT_ID)).thenReturn(false);

        assertThrows(AlertNotFoundException.class, () -> service.findByAlert(ALERT_ID));

        verifyNoInteractions(auditEventRepository);
    }
}
