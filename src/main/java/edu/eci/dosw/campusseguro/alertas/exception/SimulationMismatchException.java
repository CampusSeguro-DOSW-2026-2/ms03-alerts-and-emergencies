package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class SimulationMismatchException extends RuntimeException {

    public SimulationMismatchException(UUID alertId, String simulationId) {
        super("La alerta " + alertId + " no corresponde al simulacro " + simulationId);
    }
}
