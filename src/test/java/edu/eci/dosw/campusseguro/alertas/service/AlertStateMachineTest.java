package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;

/**
 * La tabla de transiciones es data y la verifica {@code AlertMigrationIT} contra la base. Aquí solo se
 * comprueba que la máquina de estados delega en el catálogo.
 */
class AlertStateMachineTest {

    private final AlertStateCatalog catalog = mock(AlertStateCatalog.class);

    private final AlertStateMachine stateMachine = new AlertStateMachine(catalog);

    @Test
    void canTransition_shouldAskTheCatalog() {
        when(catalog.canTransition("DRAFT", "ACTIVE")).thenReturn(true);
        when(catalog.canTransition("ARCHIVED", "ACTIVE")).thenReturn(false);

        assertTrue(stateMachine.canTransition("DRAFT", "ACTIVE"));
        assertFalse(stateMachine.canTransition("ARCHIVED", "ACTIVE"));
    }

    @Test
    void canTransition_shouldBeFalse_whenCatalogRejectsNulls() {
        when(catalog.canTransition(null, "ACTIVE")).thenReturn(false);

        assertFalse(stateMachine.canTransition(null, "ACTIVE"));
    }

    @Test
    void assertTransition_shouldPropagateTheCatalogFailure() {
        doThrow(new InvalidTransitionException("ARCHIVED", "ACTIVE"))
                .when(catalog)
                .assertTransition("ARCHIVED", "ACTIVE");

        assertThrows(
                InvalidTransitionException.class,
                () -> stateMachine.assertTransition("ARCHIVED", "ACTIVE"));
    }
}
