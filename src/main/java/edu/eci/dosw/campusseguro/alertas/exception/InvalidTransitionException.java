package edu.eci.dosw.campusseguro.alertas.exception;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;

public class InvalidTransitionException extends RuntimeException {

    private final AlertStatus currentStatus;
    private final AlertStatus requestedStatus;

    public InvalidTransitionException(AlertStatus currentStatus, AlertStatus requestedStatus) {
        super("No se puede pasar de " + currentStatus + " a " + requestedStatus);
        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public AlertStatus getCurrentStatus() {
        return currentStatus;
    }

    public AlertStatus getRequestedStatus() {
        return requestedStatus;
    }
}