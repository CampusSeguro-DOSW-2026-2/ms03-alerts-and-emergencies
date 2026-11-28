package edu.eci.dosw.campusseguro.alertas.scheduler;

import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta los correos fallidos de las alertas que siguen activas, durante una ventana desde su activación.
 *
 * <p>Usa la misma notificación de la activación, que no reenvía a quien ya tiene la entrega {@code SENT}. No
 * reintenta los fallos por el límite diario: el proveedor los rechazaría de nuevo.
 */
@Component
public class NotificationRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryScheduler.class);

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationService notificationService;
    private final Clock clock;
    private final Duration interval;
    private final Duration window;

    public NotificationRetryScheduler(
            NotificationDeliveryRepository deliveryRepository,
            NotificationService notificationService,
            Clock clock,
            @Value("${app.mail.retry-interval-ms:60000}") long intervalMs,
            @Value("${app.mail.retry-window-ms:1800000}") long windowMs) {
        this.deliveryRepository = deliveryRepository;
        this.notificationService = notificationService;
        this.clock = clock;
        this.interval = Duration.ofMillis(Math.max(0, intervalMs));
        this.window = Duration.ofMillis(Math.max(0, windowMs));
    }

    @Scheduled(
            fixedDelayString = "${app.mail.retry-interval-ms:60000}",
            initialDelayString = "${app.mail.retry-interval-ms:60000}")
    public void retryFailedDeliveries() {
        Instant now = clock.instant();
        for (UUID alertId : deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                DeliveryStatus.FAILED, now.minus(window), now.minus(interval), NotificationService.DAILY_LIMIT_REASON)) {
            MDC.put(CorrelationIds.MDC_KEY, CorrelationIds.resolve(null));
            try {
                log.info("Reintentando los correos fallidos de la alerta {}", alertId);
                notificationService.notifyAlertActivated(alertId);
            } catch (RuntimeException ex) {
                log.warn("No se pudieron reintentar los correos de la alerta {}: {}", alertId, ex.getMessage());
            } finally {
                MDC.remove(CorrelationIds.MDC_KEY);
            }
        }
    }
}
