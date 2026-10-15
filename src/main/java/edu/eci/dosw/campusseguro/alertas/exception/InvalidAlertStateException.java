package edu.eci.dosw.campusseguro.alertas.exception;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;

public class InvalidAlertStateException extends RuntimeException {

    private final AlertStatus currentStatus;

    public InvalidAlertStateException(AlertStatus currentStatus) {
        super("La alerta está en estado " + currentStatus + " y no admite esta operación");
        this.currentStatus = currentStatus;
    }

    public AlertStatus getCurrentStatus() {
        return currentStatus;
    }
}
