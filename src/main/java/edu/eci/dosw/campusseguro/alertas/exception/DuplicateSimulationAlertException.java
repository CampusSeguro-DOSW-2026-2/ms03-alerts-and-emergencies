package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class DuplicateSimulationAlertException extends RuntimeException {

    public DuplicateSimulationAlertException(
            UUID simulationId,
            UUID existingAlertId) {
        super("Ya existe una alerta viva para el simulacro "
                + simulationId + ": " + existingAlertId);
    }
}
