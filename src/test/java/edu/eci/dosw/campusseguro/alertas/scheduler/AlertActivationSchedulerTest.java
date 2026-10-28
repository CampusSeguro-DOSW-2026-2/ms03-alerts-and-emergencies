package edu.eci.dosw.campusseguro.alertas.scheduler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class AlertActivationSchedulerTest {

    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private AlertLifecycleService alertLifecycleService;

    @InjectMocks
    private AlertActivationScheduler scheduler;

    @Test
    void activateScheduledAlerts_shouldActivateEachDueAlertAsScheduler() {
        when(alertLifecycleService.findDueScheduledAlertIds()).thenReturn(List.of(FIRST, SECOND));

        scheduler.activateScheduledAlerts();

        verify(alertLifecycleService).activate(FIRST, ActorResolver.SYSTEM_SCHEDULER);
        verify(alertLifecycleService).activate(SECOND, ActorResolver.SYSTEM_SCHEDULER);
    }

    @Test
    void activateScheduledAlerts_shouldContinue_whenOneActivationFails() {
        when(alertLifecycleService.findDueScheduledAlertIds()).thenReturn(List.of(FIRST, SECOND));
        when(alertLifecycleService.activate(FIRST, ActorResolver.SYSTEM_SCHEDULER))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        scheduler.activateScheduledAlerts();

        verify(alertLifecycleService).activate(SECOND, ActorResolver.SYSTEM_SCHEDULER);
    }

    @Test
    void activateScheduledAlerts_shouldDoNothing_whenNoAlertIsDue() {
        when(alertLifecycleService.findDueScheduledAlertIds()).thenReturn(List.of());

        scheduler.activateScheduledAlerts();

        verify(alertLifecycleService, never()).activate(FIRST, ActorResolver.SYSTEM_SCHEDULER);
    }
}
