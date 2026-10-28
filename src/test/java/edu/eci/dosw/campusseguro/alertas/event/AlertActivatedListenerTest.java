package edu.eci.dosw.campusseguro.alertas.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class AlertActivatedListenerTest {

    private static final String CORRELATION_ID =
            "99999999-9999-4999-8999-999999999999";

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AlertActivatedListener listener;

    @Test
    void onAlertActivated_shouldNotifyTheAlert() {
        UUID alertId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        listener.onAlertActivated(new AlertActivatedEvent(
                alertId,
                AlertStatus.SCHEDULED,
                Instant.now(),
                CORRELATION_ID));

        verify(notificationService).notifyAlertActivated(alertId);
        verifyNoMoreInteractions(notificationService);
    }

    @Test
    void onAlertActivated_shouldPropagateCorrelationIdToMdcAndRestoreIt() {
        UUID alertId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        AtomicReference<String> observed = new AtomicReference<>();

        doAnswer(invocation -> {
            observed.set(MDC.get(CorrelationIds.MDC_KEY));
            return null;
        }).when(notificationService).notifyAlertActivated(alertId);

        listener.onAlertActivated(new AlertActivatedEvent(
                alertId,
                AlertStatus.SCHEDULED,
                Instant.now(),
                CORRELATION_ID));

        assertEquals(CORRELATION_ID, observed.get());
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }
}
