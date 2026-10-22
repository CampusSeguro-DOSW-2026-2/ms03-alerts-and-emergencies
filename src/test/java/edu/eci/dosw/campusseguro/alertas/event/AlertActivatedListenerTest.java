package edu.eci.dosw.campusseguro.alertas.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertActivatedListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AlertActivatedListener listener;

    @Test
    void onAlertActivated_shouldNotifyTheAlert() {
        UUID alertId = UUID.fromString("77777777-7777-7777-7777-777777777777");

        listener.onAlertActivated(new AlertActivatedEvent(alertId, AlertStatus.SCHEDULED, Instant.now()));

        verify(notificationService).notifyAlertActivated(alertId);
        verifyNoMoreInteractions(notificationService);
    }
}
