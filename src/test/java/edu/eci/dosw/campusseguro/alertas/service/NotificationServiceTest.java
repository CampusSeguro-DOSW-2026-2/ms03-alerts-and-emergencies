package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailMessage;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailTemplateBuilder;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
                3);
    }

    private AlertEntity alert() {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.setType(AlertType.EARTHQUAKE);
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setOrigin(AlertOrigin.MANUAL);
        alert.setInstructions("Evacuar");
        return alert;
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
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.empty());
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, LUIS.id())).thenReturn(Optional.empty());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));

        service.notifyAlertActivated(ALERT_ID);

        verify(emailSender).send(messageFor(ANA));
        verify(emailSender).send(messageFor(LUIS));
        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(List.of(DeliveryStatus.SENT, DeliveryStatus.SENT),
                saved.getAllValues().stream().map(NotificationDeliveryEntity::getStatus).toList());
        verify(auditService).record(
                ALERT_ID,
                AuditAction.NOTIFICATION_SENT,
                ActorResolver.SYSTEM_NOTIFIER,
                AlertStatus.ACTIVE,
                AlertStatus.ACTIVE,
                "Notificaciones de alerta: enviadas=2, fallidas=0");
    }

    @Test
    void notify_shouldNotResend_whenDeliveryIsAlreadySent() {
        AlertEntity alert = alert();
        NotificationDeliveryEntity sent = new NotificationDeliveryEntity();
        sent.setStatus(DeliveryStatus.SENT);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.of(sent));
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, LUIS.id())).thenReturn(Optional.empty());
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));

        service.notifyAlertActivated(ALERT_ID);

        verify(emailSender, never()).send(messageFor(ANA));
        verify(emailSender).send(messageFor(LUIS));
        verify(deliveryRepository).save(any(NotificationDeliveryEntity.class));
    }

    @Test
    void notify_shouldReuseFailedDelivery_whenRetrying() {
        AlertEntity alert = alert();
        NotificationDeliveryEntity failed = new NotificationDeliveryEntity();
        failed.setStatus(DeliveryStatus.FAILED);
        failed.setErrorReason("anterior");
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA));
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.of(failed));
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));

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
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.empty());
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, LUIS.id())).thenReturn(Optional.empty());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        when(templateBuilder.build(alert, LUIS)).thenReturn(messageFor(LUIS));
        doThrow(new EmailSendException("Brevo caído")).when(emailSender).send(messageFor(ANA));

        assertDoesNotThrow(() -> service.notifyAlertActivated(ALERT_ID));

        ArgumentCaptor<NotificationDeliveryEntity> saved =
                ArgumentCaptor.forClass(NotificationDeliveryEntity.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(DeliveryStatus.FAILED, saved.getAllValues().get(0).getStatus());
        assertEquals("Brevo caído", saved.getAllValues().get(0).getErrorReason());
        assertEquals(DeliveryStatus.SENT, saved.getAllValues().get(1).getStatus());
        verify(auditService).record(
                ALERT_ID,
                AuditAction.NOTIFICATION_FAILED,
                ActorResolver.SYSTEM_NOTIFIER,
                AlertStatus.ACTIVE,
                AlertStatus.ACTIVE,
                "Notificaciones de alerta: enviadas=1, fallidas=1");
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
                1);
        AlertEntity alert = alert();
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert));
        when(recipientResolver.resolve(ALERT_ID)).thenReturn(List.of(ANA, LUIS));
        when(deliveryRepository.countBySentAtGreaterThanEqual(any())).thenReturn(1L);
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.empty());
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, LUIS.id())).thenReturn(Optional.empty());

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
        when(deliveryRepository.findByAlertIdAndUserId(ALERT_ID, ANA.id())).thenReturn(Optional.empty());
        when(templateBuilder.build(alert, ANA)).thenReturn(messageFor(ANA));
        doThrow(new EmailSendException(longError)).when(emailSender).send(any());

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
        verify(auditService).record(
                ALERT_ID,
                AuditAction.RECIPIENT_RESOLUTION_FAILED,
                ActorResolver.SYSTEM_NOTIFIER,
                AlertStatus.ACTIVE,
                AlertStatus.ACTIVE,
                "No se pudieron resolver destinatarios: MS01 caído");
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
}
