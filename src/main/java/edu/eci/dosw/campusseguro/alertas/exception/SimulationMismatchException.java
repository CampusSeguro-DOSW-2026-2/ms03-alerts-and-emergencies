package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class SimulationMismatchException extends RuntimeException {

    public SimulationMismatchException(UUID alertId, UUID simulationId) {
        super("La alerta " + alertId
                + " no corresponde al simulacro " + simulationId);
    }
}
