
package edu.eci.dosw.campusseguro.alertas.exception;


public class InvalidTransitionException extends RuntimeException {

    private final String currentStatus;
    private final String requestedStatus;

    public InvalidTransitionException(
            String currentStatus,
            String requestedStatus) {

        super("No se puede cambiar la alerta de "
                + currentStatus + " a " + requestedStatus);

        this.currentStatus = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getRequestedStatus() {
        return requestedStatus;
    }
}
