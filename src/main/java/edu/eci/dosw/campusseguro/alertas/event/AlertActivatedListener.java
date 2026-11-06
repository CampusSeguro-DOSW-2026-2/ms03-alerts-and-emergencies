package edu.eci.dosw.campusseguro.alertas.event;

import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AlertActivatedListener {

    private final NotificationService notificationService;

    public AlertActivatedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertActivated(AlertActivatedEvent event) {
        String correlationId = CorrelationIds.resolve(event.correlationId());
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        try {
            notificationService.notifyAlertActivated(event.alertId());
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
