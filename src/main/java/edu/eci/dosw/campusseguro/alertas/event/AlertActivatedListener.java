package edu.eci.dosw.campusseguro.alertas.event;

import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
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
        notificationService.notifyAlertActivated(event.alertId());
    }
}
