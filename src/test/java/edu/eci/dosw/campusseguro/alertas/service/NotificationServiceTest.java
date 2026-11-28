package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailMessage;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailTemplateBuilder;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AuditActions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final ResolvedUser ANA = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");
    private static final ResolvedUser LUIS = new ResolvedUser("usr-002", "luis@example.edu.co", "Luis");

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private RecipientResolverService recipientResolver;

    @Mock
    private EmailTemplateBuilder templateBuilder;

    @Mock
    private EmailSender emailSender;

    @Mock
    private AuditService auditService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                alertRepository,
                deliveryRepository,
                recipientResolver,
                templateBuilder,
                emailSender,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                TransactionOperations.withoutTransaction());
    }

    private AlertEntity alert() {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.setTypes(Set.of("SISMO"));
        alert.setStatus("ACTIVE");
        alert.setOrigin(AlertOrigin.MANUAL);
        alert.setInstructions("Evacuar");
        return alert;
    }

    private NotificationDeliveryEntity delivery(ResolvedUser user, DeliveryStatus status) {
        NotificationDeliveryEntity delivery = new NotificationDeliveryEntity();
        delivery.setUserId(user.id());
        delivery.setStatus(status);
        return delivery;
    }

    private EmailMessage messageFor(ResolvedUser user) {
        return new EmailMessage(user.email(), user.name(), "Alerta", "<p>Evacuar</p>", "Evacuar");
    }

    @Test
    void notify_shouldSendOneEmailPerUniqueRecipientAndRecordAudit() {
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.countBySentAtGreaterThanEqual(Instant.parse("2026-10-15T00:00:00Z")))
                .thenReturn(0L);
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));
        when(emailSender.sendAll(List.of(messageFor(ANA), messageFor(LUIS)))).thenReturn(Map.of());

        service.notifyAlertActivated(ALERT_ID);

        verify(emailSender).sendAll(List.of(messageFor(ANA), messageFor(LUIS)));
        verify(emailSender, never()).send(any());
        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of(DeliveryStatus.SENT, DeliveryStatus.SENT),
                saved.getAllValues().stream().map(NotificationDeliveryEntity::getStatus).toList());
        ArgumentCaptor<AuditRecord> audit =
                ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditService).record(audit.capture());
        assertEquals(ALERT_ID, audit.getValue().alertId());
        assertEquals(AuditActions.NOTIFICATION_SENT, audit.getValue().action());
        assertEquals(ActorResolver.SYSTEM_NOTIFIER, audit.getValue().actor());
        assertEquals("ACTIVE", audit.getValue().fromStatus());
        assertEquals("ACTIVE", audit.getValue().toStatus());
        assertEquals("Notificaciones de alerta: enviadas=2, fallidas=0", audit.getValue().details());
        assertEquals(AuditResult.EXITO, audit.getValue().resultado());
        assertEquals(2, number(audit.getValue(), "destinatarios"));
        assertEquals(2, number(audit.getValue(), "enviados"));
        assertEquals(0, number(audit.getValue(), "fallidos"));
    }

    @Test
    void notify_shouldNotResend_whenDeliveryIsAlreadySent() {
        AlertEntity alert = alert();
        NotificationDeliveryEntity sent = delivery(ANA, DeliveryStatus.SENT);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of(sent));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));
        when(emailSender.sendAll(List.of(messageFor(LUIS)))).thenReturn(Map.of());

        service.notifyAlertActivated(ALERT_ID);

        verify(templateBuilder, never()).build(alert, ANA);
        verify(emailSender).sendAll(List.of(messageFor(LUIS)));
        verify(deliveryRepository).save(any(NotificationDeliveryEntity.class));
    }

    @Test
    void notify_shouldReuseFailedDelivery_whenRetrying() {
        AlertEntity alert = alert();
        NotificationDeliveryEntity failed = delivery(ANA, DeliveryStatus.FAILED);
        failed.setErrorReason("anterior");
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of(failed));
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(emailSender.sendAll(List.of(messageFor(ANA)))).thenReturn(Map.of());

        service.notifyAlertActivated(ALERT_ID);

        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository).save(saved.capture());
        assertEquals(failed, saved.getValue());
        assertEquals(DeliveryStatus.SENT, failed.getStatus());
        assertNull(failed.getErrorReason());
        assertEquals(NOW, failed.getSentAt());
    }

    @Test
    void notify_shouldRecordFailureAndContinue_whenOneSendFails() {
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));
        when(emailSender.sendAll(List.of(messageFor(ANA), messageFor(LUIS))))
                .thenReturn(Map.of(0, "Brevo caído"));

        assertDoesNotThrow(() -> service.notifyAlertActivated(ALERT_ID));

        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(DeliveryStatus.FAILED, saved.getAllValues().get(0).getStatus());
        assertEquals("Brevo caído", saved.getAllValues().get(0).getErrorReason());
        assertEquals(DeliveryStatus.SENT, saved.getAllValues().get(1).getStatus());
        ArgumentCaptor<AuditRecord> audit =
                ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditActions.NOTIFICATION_FAILED, audit.getValue().action());
        assertEquals(AuditResult.FALLO, audit.getValue().resultado());
        assertEquals("Notificaciones de alerta: enviadas=1, fallidas=1", audit.getValue().details());
        assertEquals(2, number(audit.getValue(), "destinatarios"));
        assertEquals(1, number(audit.getValue(), "enviados"));
        assertEquals(1, number(audit.getValue(), "fallidos"));
    }

    @Test
    void notify_shouldApplyDailyLimitBeforeSending() {
        service = new NotificationService(
                alertRepository,
                deliveryRepository,
                recipientResolver,
                templateBuilder,
                emailSender,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                1,
                TransactionOperations.withoutTransaction());
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.countBySentAtGreaterThanEqual(any())).thenReturn(1L);
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());

        service.notifyAlertActivated(ALERT_ID);

        verifyNoInteractions(emailSender, templateBuilder);
        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of(DeliveryStatus.FAILED, DeliveryStatus.FAILED),
                saved.getAllValues().stream().map(NotificationDeliveryEntity::getStatus).toList());
        assertEquals("Límite diario de correos alcanzado",
                saved.getAllValues().get(0).getErrorReason());
    }

    @Test
    void notify_shouldTruncateErrorReason() {
        AlertEntity alert = alert();
        String longError = "x".repeat(600);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(emailSender.sendAll(List.of(messageFor(ANA)))).thenReturn(Map.of(0, longError));

        service.notifyAlertActivated(ALERT_ID);

        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository).save(saved.capture());
        assertEquals(500, saved.getValue().getErrorReason().length());
    }

    @Test
    void notify_shouldRecordAuditAndNotFail_whenRecipientResolutionFails() {
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenThrow(new RuntimeException("MS01 caído"));

        assertDoesNotThrow(() -> service.notifyAlertActivated(ALERT_ID));

        verifyNoInteractions(emailSender, deliveryRepository);
        ArgumentCaptor<AuditRecord> audit =
                ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditActions.RECIPIENT_RESOLUTION_FAILED, audit.getValue().action());
        assertEquals(AuditResult.FALLO, audit.getValue().resultado());
        assertEquals("No se pudieron resolver destinatarios: MS01 caído", audit.getValue().details());
        assertEquals(0, number(audit.getValue(), "destinatarios"));
    }

    @Test
    void notify_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.notifyAlertActivated(ALERT_ID));

        verifyNoInteractions(recipientResolver, emailSender, deliveryRepository, auditService);
    }

    @Test
    void notify_shouldUseClockZoneForDailyLimit() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert()));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of());
        when(deliveryRepository.countBySentAtGreaterThanEqual(eq(Instant.parse("2026-10-15T00:00:00Z"))))
                .thenReturn(0L);

        service.notifyAlertActivated(ALERT_ID);

        verify(deliveryRepository).countBySentAtGreaterThanEqual(Instant.parse("2026-10-15T00:00:00Z"));
    }

    @Test
    void notify_shouldMarkEveryRecipientFailed_whenTheWholeBatchFails() {
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));
        when(emailSender.sendAll(any())).thenThrow(new EmailSendException("Proveedor caído"));

        assertDoesNotThrow(() -> service.notifyAlertActivated(ALERT_ID));

        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of(DeliveryStatus.FAILED, DeliveryStatus.FAILED),
                saved.getAllValues().stream().map(NotificationDeliveryEntity::getStatus).toList());
        ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditService).record(audit.capture());
        assertEquals(AuditActions.NOTIFICATION_FAILED, audit.getValue().action());
        assertEquals("Notificaciones de alerta: enviadas=0, fallidas=2", audit.getValue().details());
    }

    @Test
    void notify_shouldSendOutsideTheTransactionAndSaveResultsInside() {
        AtomicBoolean inTransaction = new AtomicBoolean();
        TransactionOperations tracking = new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                inTransaction.set(true);
                try {
                    return action.doInTransaction(null);
                } finally {
                    inTransaction.set(false);
                }
            }
        };
        service = new NotificationService(
                alertRepository,
                deliveryRepository,
                recipientResolver,
                templateBuilder,
                emailSender,
                auditService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                tracking);
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA));
        when(deliveryRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        AtomicBoolean sentInTransaction = new AtomicBoolean(true);
        AtomicBoolean savedInTransaction = new AtomicBoolean();
        AtomicBoolean auditedInTransaction = new AtomicBoolean();
        when(emailSender.sendAll(any())).thenAnswer(invocation -> {
            sentInTransaction.set(inTransaction.get());
            return Map.of();
        });
        when(deliveryRepository.save(any())).thenAnswer(invocation -> {
            savedInTransaction.set(inTransaction.get());
            return invocation.getArgument(0);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            auditedInTransaction.set(inTransaction.get());
            return null;
        }).when(auditService).record(any());

        service.notifyAlertActivated(ALERT_ID);

        assertEquals(false, sentInTransaction.get());
        assertEquals(true, savedInTransaction.get());
        assertEquals(true, auditedInTransaction.get());
    }

    private long number(AuditRecord record, String key) {
        return ((Number) record.atributos().get(key)).longValue();
    }
}
