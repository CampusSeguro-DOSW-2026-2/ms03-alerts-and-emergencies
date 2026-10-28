package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotActiveException;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.NotARecipientException;
import edu.eci.dosw.campusseguro.alertas.mapper.ConfirmationMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertConfirmationRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.AlertAccessPolicy;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConfirmationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final AccessActor ACTOR = new AccessActor("usr-001", java.util.Set.of("ESTUDIANTE"));
    private static final List<AlertRecipientEntity> RECIPIENTS = List.of(recipient());

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private AlertConfirmationRepository confirmationRepository;

    @Mock
    private AlertAccessPolicy accessPolicy;

    @Mock
    private AuditService auditService;

    @Mock
    private ConfirmationMapper mapper;

    private ConfirmationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConfirmationServiceImpl(
                alertRepository,
                recipientRepository,
                confirmationRepository,
                accessPolicy,
                auditService,
                mapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AlertRecipientEntity recipient() {
        AlertRecipientEntity recipient = new AlertRecipientEntity();
        recipient.setAlertId(ALERT_ID);
        recipient.setRecipientType(RecipientType.USER);
        recipient.setRecipientRefId(ACTOR.userId());
        return recipient;
    }

    private AlertEntity alert(AlertStatus status) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.setStatus(status);
        return alert;
    }

    private AlertConfirmationEntity confirmation() {
        AlertConfirmationEntity confirmation = new AlertConfirmationEntity();
        ReflectionTestUtils.setField(confirmation, "id", UUID.randomUUID());
        confirmation.setAlertId(ALERT_ID);
        confirmation.setUserId(ACTOR.userId());
        confirmation.setConfirmedAt(NOW);
        return confirmation;
    }

    @Test
    void confirm_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class, () -> service.confirm(ALERT_ID, ACTOR));

        verifyNoInteractions(recipientRepository, accessPolicy, confirmationRepository, auditService);
    }

    @Test
    void confirm_shouldThrowNotActive_whenAlertIsNotActive() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ENDED)));

        assertThrows(AlertNotActiveException.class, () -> service.confirm(ALERT_ID, ACTOR));

        verifyNoInteractions(recipientRepository, accessPolicy, confirmationRepository, auditService);
    }

    @Test
    void confirm_shouldThrowNotARecipient_whenPolicyRejectsActor() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(RECIPIENTS);
        doThrow(new NotARecipientException(ALERT_ID))
                .when(accessPolicy).assertCanConfirm(ALERT_ID, ACTOR, RECIPIENTS);

        assertThrows(NotARecipientException.class, () -> service.confirm(ALERT_ID, ACTOR));

        verifyNoInteractions(confirmationRepository, auditService);
    }

    @Test
    void confirm_shouldReturnExisting_whenActorAlreadyConfirmed() {
        AlertConfirmationEntity existing = confirmation();
        ConfirmationResponse response = new ConfirmationResponse(
                existing.getId(), ALERT_ID, ACTOR.userId(), NOW);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(RECIPIENTS);
        when(confirmationRepository.findByAlertIdAndUserId(ALERT_ID, ACTOR.userId()))
                .thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(response);

        ConfirmationResult result = service.confirm(ALERT_ID, ACTOR);

        assertFalse(result.created());
        assertSame(response, result.confirmation());
        verify(confirmationRepository, never()).insertIfAbsent(any(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void confirm_shouldCreateAndAudit_whenActorIsRecipient() {
        AlertConfirmationEntity created = confirmation();
        ConfirmationResponse response = new ConfirmationResponse(
                created.getId(), ALERT_ID, ACTOR.userId(), NOW);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(RECIPIENTS);
        when(confirmationRepository.findByAlertIdAndUserId(ALERT_ID, ACTOR.userId()))
                .thenReturn(Optional.empty(), Optional.of(created));
        when(confirmationRepository.insertIfAbsent(ALERT_ID, ACTOR.userId(), NOW)).thenReturn(1);
        when(mapper.toResponse(created)).thenReturn(response);

        ConfirmationResult result = service.confirm(ALERT_ID, ACTOR);

        assertTrue(result.created());
        assertSame(response, result.confirmation());
        verify(accessPolicy).assertCanConfirm(ALERT_ID, ACTOR, RECIPIENTS);
        verify(auditService).record(
                ALERT_ID,
                AuditAction.CONFIRM,
                ACTOR.userId(),
                AlertStatus.ACTIVE,
                AlertStatus.ACTIVE,
                "Recepción confirmada por " + ACTOR.userId());
    }

    @Test
    void confirm_shouldReturnExistingWithoutAudit_whenConcurrentInsertDidNothing() {
        AlertConfirmationEntity existing = confirmation();
        ConfirmationResponse response = new ConfirmationResponse(
                existing.getId(), ALERT_ID, ACTOR.userId(), NOW);
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(RECIPIENTS);
        when(confirmationRepository.findByAlertIdAndUserId(ALERT_ID, ACTOR.userId()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(confirmationRepository.insertIfAbsent(ALERT_ID, ACTOR.userId(), NOW)).thenReturn(0);
        when(mapper.toResponse(existing)).thenReturn(response);

        ConfirmationResult result = service.confirm(ALERT_ID, ACTOR);

        assertFalse(result.created());
        verifyNoInteractions(auditService);
    }

    @Test
    void confirm_shouldFail_whenInsertDidNotPersistConfirmation() {
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(RECIPIENTS);
        when(confirmationRepository.findByAlertIdAndUserId(ALERT_ID, ACTOR.userId()))
                .thenReturn(Optional.empty());
        when(confirmationRepository.insertIfAbsent(eq(ALERT_ID), eq(ACTOR.userId()), any()))
                .thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.confirm(ALERT_ID, ACTOR));
        verifyNoInteractions(auditService);
    }

    @Test
    void confirm_shouldWork_whenActorBelongsToRecipientRole() {
        AlertRecipientEntity role = new AlertRecipientEntity();
        role.setAlertId(ALERT_ID);
        role.setRecipientType(RecipientType.ROLE);
        role.setRecipientRefId("ESTUDIANTE");
        when(alertRepository.findById(ALERT_ID)).thenReturn(Optional.of(alert(AlertStatus.ACTIVE)));
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(List.of(role));
        when(confirmationRepository.insertIfAbsent(ALERT_ID, ACTOR.userId(), NOW)).thenReturn(1);
        AlertConfirmationEntity created = confirmation();
        when(confirmationRepository.findByAlertIdAndUserId(ALERT_ID, ACTOR.userId()))
                .thenReturn(Optional.empty(), Optional.of(created));
        when(mapper.toResponse(created)).thenReturn(mockResponse(created));

        ConfirmationResult result = service.confirm(ALERT_ID, ACTOR);

        assertTrue(result.created());
        verify(accessPolicy).assertCanConfirm(ALERT_ID, ACTOR, List.of(role));
    }

    private ConfirmationResponse mockResponse(AlertConfirmationEntity confirmation) {
        return new ConfirmationResponse(
                confirmation.getId(), ALERT_ID, ACTOR.userId(), NOW);
    }

    @Test
    void confirm_shouldExposeMappedConfirmationDate() {
        assertEquals(NOW, confirmation().getConfirmedAt());
    }
}
