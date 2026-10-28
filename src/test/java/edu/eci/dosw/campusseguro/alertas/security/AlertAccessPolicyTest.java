package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.NotARecipientException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertAccessPolicyTest {

    private static final UUID ALERT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    private AlertAccessPolicy policy;
    private AlertEntity alert;

    @BeforeEach
    void setUp() {
        policy = new AlertAccessPolicy(recipientRepository, deliveryRepository);
        alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
    }

    private AlertRecipientEntity recipient(RecipientType type, String refId) {
        AlertRecipientEntity recipient = new AlertRecipientEntity();
        recipient.setAlertId(ALERT_ID);
        recipient.setRecipientType(type);
        recipient.setRecipientRefId(refId);
        return recipient;
    }

    @Test
    void assertCanView_shouldAllowPrivilegedRoles() {
        for (String role : List.of("COORDINADOR", "ORGANIZADOR", "AUDITOR")) {
            AccessActor actor = new AccessActor("usr-x", Set.of(role));
            assertDoesNotThrow(() -> policy.assertCanView(alert, actor, List.of()));
        }
    }

    @Test
    void assertCanView_shouldAllowDirectRecipient() {
        AccessActor actor = new AccessActor("usr-001", Set.of("ESTUDIANTE"));
        List<AlertRecipientEntity> recipients = List.of(recipient(RecipientType.USER, "usr-001"));

        assertDoesNotThrow(() -> policy.assertCanView(alert, actor, recipients));
    }

    @Test
    void assertCanView_shouldAllowUserBelongingToRecipientRole() {
        AccessActor actor = new AccessActor("usr-001", Set.of("ESTUDIANTE"));
        List<AlertRecipientEntity> recipients = List.of(recipient(RecipientType.ROLE, "ESTUDIANTE"));

        assertDoesNotThrow(() -> policy.assertCanView(alert, actor, recipients));
    }

    @Test
    void assertCanView_shouldAllowUserWithDeliveryRecord() {
        AccessActor actor = new AccessActor("usr-001", Set.of("ESTUDIANTE"));
        when(deliveryRepository.existsByAlertIdAndUserId(ALERT_ID, "usr-001")).thenReturn(true);

        assertDoesNotThrow(() -> policy.assertCanView(alert, actor, List.of()));
    }

    @Test
    void assertCanView_shouldRejectGenericUserWithoutRelationship() {
        AccessActor actor = new AccessActor("usr-999", Set.of("ESTUDIANTE"));
        when(deliveryRepository.existsByAlertIdAndUserId(ALERT_ID, "usr-999")).thenReturn(false);

        assertThrows(NotARecipientException.class, () -> policy.assertCanView(alert, actor, List.of()));
    }

    @Test
    void assertCanView_shouldLoadRecipients_whenNotProvided() {
        AccessActor actor = new AccessActor("usr-001", Set.of("ESTUDIANTE"));
        when(recipientRepository.findByAlertId(ALERT_ID))
                .thenReturn(List.of(recipient(RecipientType.USER, "usr-001")));

        assertDoesNotThrow(() -> policy.assertCanView(alert, actor));
    }

    @Test
    void assertCanConfirm_shouldAllowDirectRecipientAndDeliveredUser() {
        AccessActor direct = new AccessActor("usr-001", Set.of("ESTUDIANTE"));
        AccessActor delivered = new AccessActor("usr-002", Set.of("PROFESOR"));
        when(deliveryRepository.existsByAlertIdAndUserId(ALERT_ID, "usr-002")).thenReturn(true);

        assertDoesNotThrow(() -> policy.assertCanConfirm(
                ALERT_ID, direct, List.of(recipient(RecipientType.USER, "usr-001"))));
        assertDoesNotThrow(() -> policy.assertCanConfirm(ALERT_ID, delivered, List.of()));
    }

    @Test
    void assertCanConfirm_shouldRejectPrivilegedUserWhoIsNotRecipient() {
        AccessActor coordinator = new AccessActor("coord-1", Set.of("COORDINADOR"));
        when(deliveryRepository.existsByAlertIdAndUserId(ALERT_ID, "coord-1")).thenReturn(false);

        assertThrows(NotARecipientException.class,
                () -> policy.assertCanConfirm(ALERT_ID, coordinator, List.of()));
    }
}
