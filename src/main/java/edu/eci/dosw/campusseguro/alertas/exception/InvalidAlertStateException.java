
package edu.eci.dosw.campusseguro.alertas.exception;


public class InvalidAlertStateException extends RuntimeException {

    private final String currentStatus;

    public InvalidAlertStateException(String currentStatus) {
        super("La alerta está en estado " + currentStatus
                + " y no permite esta operación");
        this.currentStatus = currentStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
