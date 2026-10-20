package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import org.junit.jupiter.api.Test;

class AlertStateRulesTest {

    private final AlertStateRules rules = new AlertStateRules();

    private AlertEntity alert(AlertStatus status) {
        AlertEntity alert = new AlertEntity();
        alert.setStatus(status);
        return alert;
    }

    @Test
    void shouldAllowEditingDraftAndScheduled() {
        assertTrue(rules.isEditable(AlertStatus.DRAFT));
        assertTrue(rules.isEditable(AlertStatus.SCHEDULED));
        assertDoesNotThrow(() -> rules.assertEditable(alert(AlertStatus.DRAFT)));
        assertDoesNotThrow(() -> rules.assertEditable(alert(AlertStatus.SCHEDULED)));
    }

    @Test
    void shouldRejectEditingOtherStates() {
        assertFalse(rules.isEditable(AlertStatus.ACTIVE));
        assertFalse(rules.isEditable(AlertStatus.ENDED));
        assertFalse(rules.isEditable(AlertStatus.ARCHIVED));
        assertThrows(InvalidAlertStateException.class,
                () -> rules.assertEditable(alert(AlertStatus.ACTIVE)));
    }

    @Test
    void shouldRejectChangesOnlyForArchivedAlerts() {
        assertDoesNotThrow(() -> rules.assertNotArchived(alert(AlertStatus.ENDED)));
        assertThrows(InvalidAlertStateException.class,
                () -> rules.assertNotArchived(alert(AlertStatus.ARCHIVED)));
    }
}
