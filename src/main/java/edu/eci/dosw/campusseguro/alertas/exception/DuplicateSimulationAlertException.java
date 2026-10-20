package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class DuplicateSimulationAlertException extends RuntimeException {

    public DuplicateSimulationAlertException(String simulationId, UUID existingAlertId) {
        super("Ya existe una alerta vigente para el simulacro " + simulationId
                + " con datos distintos: " + existingAlertId);
    }
}
