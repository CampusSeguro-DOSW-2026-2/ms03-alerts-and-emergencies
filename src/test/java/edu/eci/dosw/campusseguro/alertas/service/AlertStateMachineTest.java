package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import org.junit.jupiter.api.Test;

class AlertStateMachineTest {

    private final AlertStateMachine stateMachine = new AlertStateMachine();

    @Test
    void shouldAllowEveryValidTransition() {
        assertTrue(stateMachine.canTransition(AlertStatus.DRAFT, AlertStatus.SCHEDULED));
        assertTrue(stateMachine.canTransition(AlertStatus.DRAFT, AlertStatus.ACTIVE));
        assertTrue(stateMachine.canTransition(AlertStatus.SCHEDULED, AlertStatus.ACTIVE));
        assertTrue(stateMachine.canTransition(AlertStatus.ACTIVE, AlertStatus.ENDED));
        assertTrue(stateMachine.canTransition(AlertStatus.ENDED, AlertStatus.ARCHIVED));
        assertDoesNotThrow(() -> stateMachine.assertTransition(AlertStatus.DRAFT, AlertStatus.ACTIVE));
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertFalse(stateMachine.canTransition(AlertStatus.ACTIVE, AlertStatus.DRAFT));
        assertFalse(stateMachine.canTransition(AlertStatus.ARCHIVED, AlertStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> stateMachine.assertTransition(AlertStatus.ENDED, AlertStatus.ACTIVE));
    }

    @Test
    void archivedShouldNotTransitionAnywhere() {
        for (AlertStatus status : AlertStatus.values()) {
            assertFalse(stateMachine.canTransition(AlertStatus.ARCHIVED, status));
        }
    }
}
