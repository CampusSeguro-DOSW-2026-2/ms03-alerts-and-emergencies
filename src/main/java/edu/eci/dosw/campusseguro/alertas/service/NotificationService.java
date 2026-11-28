package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailMessage;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailTemplateBuilder;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AuditRecord;
import edu.eci.dosw.campusseguro.alertas.service.AuditResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ERROR_LENGTH = 500;
    public static final String DAILY_LIMIT_REASON = "Límite diario de correos alcanzado";

    private final AlertRepository alertRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final RecipientResolverService recipientResolver;
    private final EmailTemplateBuilder templateBuilder;
    private final EmailSender emailSender;
    private final AuditService auditService;
    private final Clock clock;
    private final int dailyLimit;
    private final TransactionOperations transactions;

    public NotificationService(
            AlertRepository alertRepository,
            NotificationDeliveryRepository deliveryRepository,
            RecipientResolverService recipientResolver,
            EmailTemplateBuilder templateBuilder,
            EmailSender emailSender,
            AuditService auditService,
            Clock clock,
            @Value("${app.mail.daily-limit:300}") int dailyLimit,
            TransactionOperations transactions) {
        this.alertRepository = alertRepository;
        this.deliveryRepository = deliveryRepository;
        this.recipientResolver = recipientResolver;
        this.templateBuilder = templateBuilder;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
        this.transactions = transactions;
    }

    /**
     * Notifica una alerta activa a todos sus destinatarios a la vez.
     *
     * <p>El envío ocurre fuera de una transacción: primero se lee la alerta y se resuelven los destinatarios, luego
     * sale el lote completo y al final se guardan, en una transacción corta, las entregas ({@code SENT} o
     * {@code FAILED}) y la auditoría. Así la base no queda tomada mientras el proveedor responde. Quien ya tiene
     * una entrega {@code SENT} no se vuelve a notificar.
     */
    public void notifyAlertActivated(UUID alertId) {
        AlertEntity alert = transactions.execute(status -> loadAlert(alertId));

        List<ResolvedUser> recipients;
        try {
            recipients = recipientResolver.resolve(alertId);
        } catch (RuntimeException ex) {
            log.error("No se pudieron resolver los destinatarios de la alerta {}", alertId, ex);
            transactions.executeWithoutResult(status -> auditService.record(AuditRecord.builder(
                            alertId,
                            AuditActions.RECIPIENT_RESOLUTION_FAILED,
                            ActorResolver.SYSTEM_NOTIFIER)
                    .transition(alert.getStatus(), alert.getStatus())
                    .details(truncate("No se pudieron resolver destinatarios: " + ex.getMessage()))
                    .eventoId(alert.getSimulationId())
                    .resultado(AuditResult.FALLO)
                    .atributo("destinatarios", 0)
                    .build()));
            return;
        }

        Instant now = clock.instant();
        Map<String, NotificationDeliveryEntity> existing = recipients.isEmpty()
                ? Map.of()
                : deliveryRepository.findByAlertId(alertId).stream()
                        .collect(Collectors.toMap(
                                NotificationDeliveryEntity::getUserId, Function.identity(), (a, b) -> a));
        long sentToday = deliveryRepository.countBySentAtGreaterThanEqual(startOfToday(now));

        List<ResolvedUser> toSend = new ArrayList<>();
        List<ResolvedUser> overLimit = new ArrayList<>();
        for (ResolvedUser recipient : recipients) {
            NotificationDeliveryEntity delivery = existing.get(recipient.id());
            if (delivery != null && delivery.getStatus() == DeliveryStatus.SENT) {
                continue;
            }
            if (sentToday + toSend.size() >= dailyLimit) {
                overLimit.add(recipient);
            } else {
                toSend.add(recipient);
            }
        }

        List<EmailMessage> messages = new ArrayList<>(toSend.size());
        for (ResolvedUser recipient : toSend) {
            messages.add(templateBuilder.build(alert, recipient));
        }
        Map<Integer, String> failures = messages.isEmpty() ? Map.of() : sendAll(alertId, messages);

        Instant sentAt = clock.instant();
        transactions.executeWithoutResult(status -> {
            int sent = 0;
            int failed = 0;
            for (int i = 0; i < toSend.size(); i++) {
                ResolvedUser recipient = toSend.get(i);
                String reason = failures.get(i);
                if (reason == null) {
                    saveSent(alertId, recipient, existing.get(recipient.id()), sentAt);
                    sent++;
                } else {
                    log.warn("No se pudo enviar la notificación de la alerta {} a {}", alertId, recipient.id());
                    saveFailure(alertId, recipient, existing.get(recipient.id()), sentAt, reason);
                    failed++;
                }
            }
            for (ResolvedUser recipient : overLimit) {
                saveFailure(alertId, recipient, existing.get(recipient.id()), sentAt, DAILY_LIMIT_REASON);
                failed++;
            }
            recordResult(alert, recipients.size(), sent, failed);
        });
    }

    private AlertEntity loadAlert(UUID alertId) {
        AlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));
        // Los tipos se cargan aquí: el correo se arma después, fuera de la transacción.
        Hibernate.initialize(alert.getTypes());
        return alert;
    }

    private Map<Integer, String> sendAll(UUID alertId, List<EmailMessage> messages) {
        long start = System.nanoTime();
        Map<Integer, String> failures;
        try {
            failures = emailSender.sendAll(messages);
        } catch (EmailSendException ex) {
            failures = new HashMap<>();
            for (int i = 0; i < messages.size(); i++) {
                failures.put(i, ex.getMessage());
            }
        }
        log.info("Alerta {}: {} correos entregados al proveedor en {} ms ({} fallidos)",
                alertId, messages.size() - failures.size(), (System.nanoTime() - start) / 1_000_000,
                failures.size());
        return failures;
    }

    private void recordResult(AlertEntity alert, int recipients, int sent, int failed) {
        String details = "Notificaciones de alerta: enviadas=" + sent + ", fallidas=" + failed;
        auditService.record(AuditRecord.builder(
                        alert.getId(),
                        failed == 0 ? AuditActions.NOTIFICATION_SENT : AuditActions.NOTIFICATION_FAILED,
                        ActorResolver.SYSTEM_NOTIFIER)
                .transition(alert.getStatus(), alert.getStatus())
                .details(details)
                .eventoId(alert.getSimulationId())
                .resultado(failed == 0 ? AuditResult.EXITO : AuditResult.FALLO)
                .atributo("destinatarios", recipients)
                .atributo("enviados", sent)
                .atributo("fallidos", failed)
                .build());
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
