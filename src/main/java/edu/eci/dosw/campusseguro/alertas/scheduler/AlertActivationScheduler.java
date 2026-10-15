
package edu.eci.dosw.campusseguro.alertas.scheduler;

import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertActivationScheduler {

    private final AlertLifecycleService alertLifecycleService;

    public AlertActivationScheduler(AlertLifecycleService alertLifecycleService) {
        this.alertLifecycleService = alertLifecycleService;
    }

    @Scheduled(fixedDelayString = "${app.alerts.activation-check-ms:10000}")
    public void activateScheduledAlerts() {
        alertLifecycleService.activateScheduledAlerts();
    }
}
