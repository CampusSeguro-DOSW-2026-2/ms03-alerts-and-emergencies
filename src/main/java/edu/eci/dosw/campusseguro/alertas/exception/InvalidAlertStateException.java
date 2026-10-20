
package edu.eci.dosw.campusseguro.alertas.exception;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;

public class InvalidAlertStateException extends RuntimeException {

    private final AlertStatus currentStatus;

    public InvalidAlertStateException(AlertStatus currentStatus) {
        super("The alert is in status " + currentStatus
                + " and does not allow this operation");
        this.currentStatus = currentStatus;
    }

    public AlertStatus getCurrentStatus() {
        return currentStatus;
    }
}
