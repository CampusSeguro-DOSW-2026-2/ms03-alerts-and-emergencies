package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import org.junit.jupiter.api.Test;

/**
 * Qué estados son editables y cuál es terminal son columnas del catálogo, así que aquí se comprueba
 * que las reglas las consultan en lugar de decidirlas por su cuenta.
 */
class AlertStateRulesTest {

    private final AlertStateCatalog catalog = mock(AlertStateCatalog.class);

    private final AlertStateRules rules = new AlertStateRules(catalog);

    private AlertEntity alert(String status) {
        AlertEntity alert = new AlertEntity();
        alert.setStatus(status);
        return alert;
    }

    @Test
    void isEditable_shouldAskTheCatalog() {
        when(catalog.isEditable("DRAFT")).thenReturn(true);
        when(catalog.isEditable("ACTIVE")).thenReturn(false);

        assertTrue(rules.isEditable(alert("DRAFT")));
        assertFalse(rules.isEditable(alert("ACTIVE")));
    }

    @Test
    void assertEditable_shouldFail_whenCatalogSaysNotEditable() {
        when(catalog.isEditable("ACTIVE")).thenReturn(false);
        when(catalog.isEditable("DRAFT")).thenReturn(true);

        assertDoesNotThrow(() -> rules.assertEditable(alert("DRAFT")));
        assertThrows(
                InvalidAlertStateException.class,
                () -> rules.assertEditable(alert("ACTIVE")));
    }

    @Test
    void assertNotArchived_shouldFailOnlyForFinalStates() {
        when(catalog.isFinal("ARCHIVED")).thenReturn(true);
        when(catalog.isFinal("ACTIVE")).thenReturn(false);

        assertDoesNotThrow(() -> rules.assertNotArchived(alert("ACTIVE")));
        assertThrows(
                InvalidAlertStateException.class,
                () -> rules.assertNotArchived(alert("ARCHIVED")));
    }
}
