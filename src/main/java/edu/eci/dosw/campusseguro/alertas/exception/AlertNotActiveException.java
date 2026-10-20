package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class AlertNotActiveException extends RuntimeException {

    public AlertNotActiveException(UUID alertId) {
        super("La alerta " + alertId + " no esta activa");
    }
}
