package edu.eci.dosw.campusseguro.alertas.scheduler;

import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AlertActivationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AlertActivationScheduler.class);

    private static final RequesterContext SYSTEM =
            new RequesterContext(ActorResolver.SYSTEM_SCHEDULER, Set.of(), null);

    private final AlertLifecycleService alertLifecycleService;

    public AlertActivationScheduler(AlertLifecycleService alertLifecycleService) {
        this.alertLifecycleService = alertLifecycleService;
    }

    // Cada activación corre en su propia transacción: si una falla, las demás se procesan igual.
    @Scheduled(fixedDelayString = "${app.alerts.activation-check-ms:10000}")
    public void activateScheduledAlerts() {
        for (UUID alertId : alertLifecycleService.findDueScheduledAlertIds()) {
            String correlationId = CorrelationIds.resolve(null);
            MDC.put(CorrelationIds.MDC_KEY, correlationId);
            try {
                alertLifecycleService.activate(alertId, SYSTEM, correlationId);
            } catch (RuntimeException ex) {
                log.warn("No se pudo activar la alerta programada {}: {}", alertId, ex.getMessage());
            } finally {
                MDC.remove(CorrelationIds.MDC_KEY);
            }
        }
    }
}
