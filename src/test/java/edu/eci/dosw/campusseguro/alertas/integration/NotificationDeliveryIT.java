package edu.eci.dosw.campusseguro.alertas.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.email.EmailMessage;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.scheduler.AlertActivationScheduler;
import edu.eci.dosw.campusseguro.alertas.scheduler.NotificationRetryScheduler;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Notificación real contra PostgreSQL: el lote sale fuera de la transacción y las entregas y la auditoría se
 * guardan después. El proveedor de correo se simula.
 */
@SpringBootTest(properties = {
        "app.alerts.activation-check-ms=3600000",
        "app.audit.dispatch-interval-ms=3600000"
})
@ActiveProfiles({"test", "local"})
class NotificationDeliveryIT {

    private static final String ANDRES = "22222222-2222-4222-8222-222222222222";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AlertApiIntegrationIT.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AlertApiIntegrationIT.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AlertApiIntegrationIT.POSTGRES::getPassword);
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertRecipientRepository recipientRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private AlertActivationScheduler alertActivationScheduler;

    @MockitoBean
    private NotificationRetryScheduler notificationRetryScheduler;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_outbox, audit_events, notification_deliveries, "
                + "alert_confirmations, alert_recipients, alert_scope_items, alert_types, alerts CASCADE");
    }

    @Test
    void notify_shouldSendTheWholeBatchOnceAndSaveEveryDeliveryAndTheAudit() {
        AlertEntity alert = activeAlertFor("ESTUDIANTE", "PROFESOR");
        when(emailSender.sendAll(anyList())).thenReturn(Map.of());

        notificationService.notifyAlertActivated(alert.getId());

        ArgumentCaptor<List<EmailMessage>> batch = messagesCaptor();
        verify(emailSender).sendAll(batch.capture());
        // ESTUDIANTE: 2 personas; PROFESOR: 2 personas en el catálogo de prueba.
        assertEquals(4, batch.getValue().size());
        List<NotificationDeliveryEntity> deliveries = deliveryRepository.findByAlertId(alert.getId());
        assertEquals(4, deliveries.size());
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == DeliveryStatus.SENT && d.getSentAt() != null));
        assertEquals(1, auditCount(alert.getId(), AuditActions.NOTIFICATION_SENT));
        assertEquals("ACTIVE", alertRepository.findById(alert.getId()).orElseThrow().getStatus());
    }

    @Test
    void retry_shouldResendOnlyTheFailedRecipientOfAnActiveAlert() {
        AlertEntity alert = activeAlertFor("ESTUDIANTE");
        when(emailSender.sendAll(anyList())).thenAnswer(invocation -> {
            List<EmailMessage> messages = invocation.getArgument(0);
            return messages.size() == 2 ? Map.of(1, "Brevo respondió 503") : Map.of();
        });
        notificationService.notifyAlertActivated(alert.getId());
        assertEquals(1, auditCount(alert.getId(), AuditActions.NOTIFICATION_FAILED));
        String failedUser = deliveryRepository.findByAlertId(alert.getId()).stream()
                .filter(d -> d.getStatus() == DeliveryStatus.FAILED)
                .map(NotificationDeliveryEntity::getUserId)
                .findFirst().orElseThrow();

        jdbcTemplate.update("UPDATE notification_deliveries SET updated_at = now() - interval '5 minutes'");
        Instant now = Instant.now();
        List<UUID> toRetry = deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                DeliveryStatus.FAILED, now.minus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES),
                NotificationService.DAILY_LIMIT_REASON);
        assertEquals(List.of(alert.getId()), toRetry);

        notificationService.notifyAlertActivated(alert.getId());

        ArgumentCaptor<List<EmailMessage>> batches = messagesCaptor();
        verify(emailSender, org.mockito.Mockito.times(2)).sendAll(batches.capture());
        assertEquals(1, batches.getAllValues().get(1).size());
        assertTrue(deliveryRepository.findByAlertId(alert.getId()).stream()
                .allMatch(d -> d.getStatus() == DeliveryStatus.SENT));
        assertEquals(Set.of(ANDRES, "11111111-1111-4111-8111-111111111111"),
                Set.copyOf(deliveryRepository.findByAlertId(alert.getId()).stream()
                        .map(NotificationDeliveryEntity::getUserId).toList()));
        assertTrue(Set.of(ANDRES, "11111111-1111-4111-8111-111111111111").contains(failedUser));
        assertEquals(1, auditCount(alert.getId(), AuditActions.NOTIFICATION_SENT));
    }

    @Test
    void retryQuery_shouldIgnoreArchivedAlertsAndDailyLimitFailures() {
        AlertEntity archived = activeAlertFor("ESTUDIANTE");
        AlertEntity limited = activeAlertFor("PROFESOR");
        when(emailSender.sendAll(any())).thenReturn(Map.of(0, "Brevo respondió 503", 1, "Brevo respondió 503"));
        notificationService.notifyAlertActivated(archived.getId());
        jdbcTemplate.update("UPDATE alerts SET status = 'ARCHIVED', archived_at = now() WHERE id = ?",
                archived.getId());
        jdbcTemplate.update("INSERT INTO notification_deliveries (id, alert_id, user_id, email, status, error_reason, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, 'FAILED', ?, now(), now())",
                UUID.randomUUID(), limited.getId(), "33333333-3333-4333-8333-333333333333",
                "marta.pineda@example.edu.co", NotificationService.DAILY_LIMIT_REASON);
        jdbcTemplate.update("UPDATE notification_deliveries SET updated_at = now() - interval '5 minutes'");

        Instant now = Instant.now();
        assertTrue(deliveryRepository.findAlertIdsWithDeliveriesToRetry(
                DeliveryStatus.FAILED, now.minus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES),
                NotificationService.DAILY_LIMIT_REASON).isEmpty());
    }

    private AlertEntity activeAlertFor(String... roles) {
        Instant now = Instant.now();
        AlertEntity alert = new AlertEntity();
        alert.setTypes(Set.of("SISMO"));
        alert.setTitle("Simulacro sismico");
        alert.setStatus("ACTIVE");
        alert.setInstructions("Evacuar el campus");
        alert.setOrigin(AlertOrigin.MANUAL);
        alert.setCreatedBy("it-user");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        alert.setActivatedAt(now);
        AlertEntity saved = alertRepository.save(alert);
        for (String role : roles) {
            AlertRecipientEntity recipient = new AlertRecipientEntity();
            recipient.setAlertId(saved.getId());
            recipient.setRecipientType(RecipientType.ROLE);
            recipient.setRecipientRefId(role);
            recipientRepository.save(recipient);
        }
        return saved;
    }

    private long auditCount(UUID alertId, String action) {
        return auditEventRepository.findByAlertIdOrderByCreatedAtAsc(alertId).stream()
                .filter(event -> action.equals(event.getAction()))
                .count();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<EmailMessage>> messagesCaptor() {
        return ArgumentCaptor.forClass((Class<List<EmailMessage>>) (Class<?>) List.class);
    }
}
