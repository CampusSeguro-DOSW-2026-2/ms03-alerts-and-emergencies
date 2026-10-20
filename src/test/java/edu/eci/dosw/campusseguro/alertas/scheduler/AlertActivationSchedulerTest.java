package edu.eci.dosw.campusseguro.alertas.scheduler;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertActivationSchedulerTest {

    @Mock
    private AlertLifecycleService alertLifecycleService;

    @InjectMocks
    private AlertActivationScheduler scheduler;

    @Test
    void activateScheduledAlerts_shouldDelegateToLifecycleService() {
        scheduler.activateScheduledAlerts();
        scheduler.activateScheduledAlerts();

        verify(alertLifecycleService, times(2)).activateScheduledAlerts();
    }
}
