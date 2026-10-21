package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailTemplateBuilder;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final AlertRepository alertRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final RecipientResolverService recipientResolver;
    private final EmailTemplateBuilder templateBuilder;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final Clock clock;
    private final int dailyLimit;

    public NotificationService(
            AlertRepository alertRepository,
            NotificationDeliveryRepository deliveryRepository,
            RecipientResolverService recipientResolver,
            EmailTemplateBuilder templateBuilder,
            EmailSender emailSender,
            AuditService auditService,
            Clock clock,
            @Value("${app.mail.daily-limit:300}") int dailyLimit) {
        this.alertRepository = alertRepository;
        this.deliveryRepository = deliveryRepository;
        this.recipientResolver = recipientResolver;
        this.templateBuilder = templateBuilder;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    @Transactional
    public void notifyAlertActivated(UUID alertId) {
        AlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        List<ResolvedUser> recipients;
        try {
            recipients = recipientResolver.resolve(alertId);
        } catch (RuntimeException ex) {
            log.error("No se pudieron resolver los destinatarios de la alerta {}", alertId, ex);
            auditService.record(
                    alertId,
                    AuditAction.RECIPIENT_RESOLUTION_FAILED,
                    ActorResolver.SYSTEM_NOTIFIER,
                    alert.getStatus(),
                    alert.getStatus(),
                    truncate("No se pudieron resolver destinatarios: " + ex.getMessage()));
            return;
        }

        Instant now = clock.instant();
        long sentToday = deliveryRepository.countBySentAtGreaterThanEqual(startOfToday(now));
        int sent = 0;
        int failed = 0;

        for (ResolvedUser recipient : recipients) {
            NotificationDeliveryEntity delivery = deliveryRepository
                    .findByAlertIdAndUserId(alertId, recipient.id())
                    .orElse(null);

            if (delivery != null && delivery.getStatus() == DeliveryStatus.SENT) {
                continue;
            }

            if (sentToday + sent >= dailyLimit) {
                saveFailure(alertId, recipient, delivery, now, "Límite diario de correos alcanzado");
                failed++;
                continue;
            }

            try {
                emailSender.send(templateBuilder.build(alert, recipient));
            } catch (RuntimeException ex) {
                log.warn("No se pudo enviar la notificación de la alerta {} a {}", alertId, recipient.id());
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                saveFailure(alertId, recipient, delivery, now, reason);
                failed++;
                continue;
            }
            saveSent(alertId, recipient, delivery, now);
            sent++;
        }

        String details = "Notificaciones de alerta: enviadas=" + sent + ", fallidas=" + failed;
        auditService.record(
                alertId,
                failed == 0 ? AuditAction.NOTIFICATION_SENT : AuditAction.NOTIFICATION_FAILED,
                ActorResolver.SYSTEM_NOTIFIER,
                alert.getStatus(),
                alert.getStatus(),
                details);
    }

    private void saveSent(UUID alertId, ResolvedUser recipient, NotificationDeliveryEntity existing, Instant now) {
        NotificationDeliveryEntity delivery = existing == null ? newNotification(alertId, recipient, now) : existing;
        delivery.setEmail(recipient.email());
        delivery.setStatus(DeliveryStatus.SENT);
        delivery.setErrorReason(null);
        delivery.setSentAt(now);
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
    }

    private void saveFailure(
            UUID alertId, ResolvedUser recipient, NotificationDeliveryEntity existing, Instant now, String reason) {
        NotificationDeliveryEntity delivery = existing == null ? newNotification(alertId, recipient, now) : existing;
        delivery.setEmail(recipient.email());
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setErrorReason(truncate(reason));
        delivery.setSentAt(null);
        delivery.setUpdatedAt(now);
        deliveryRepository.save(delivery);
    }

    private NotificationDeliveryEntity newNotification(UUID alertId, ResolvedUser recipient, Instant now) {
        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.setAlertId(alertId);
        delivery.setUserId(recipient.id());
        delivery.setEmail(recipient.email());
        delivery.setCreatedAt(now);
        delivery.setUpdatedAt(now);
        return delivery;
    }

    private Instant startOfToday(Instant now) {
        return LocalDate.ofInstant(now, clock.getZone())
                .atStartOfDay(clock.getZone())
                .toInstant();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
