package edu.eci.dosw.campusseguro.alertas.scheduler;

import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertActivationScheduler.class);

    private final AlertLifecycleService alertLifecycleService;

    public AlertActivationScheduler(AlertLifecycleService alertLifecycleService) {
        this.alertLifecycleService = alertLifecycleService;
    }

    // Cada activación corre en su propia transacción: si una falla, las demás se procesan igual.
    @Scheduled(fixedDelayString = "${app.alerts.activation-check-ms:10000}")
    public void activateScheduledAlerts() {
        for (UUID alertId : alertLifecycleService.findDueScheduledAlertIds()) {
            try {
                alertLifecycleService.activate(alertId, ActorResolver.SYSTEM_SCHEDULER);
            } catch (RuntimeException ex) {
                log.warn("Could not activate scheduled alert {}: {}", alertId, ex.getMessage());
            }
        }
    }
}
