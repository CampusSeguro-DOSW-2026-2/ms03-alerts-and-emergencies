
package edu.eci.dosw.campusseguro.alertas.exception;

public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String service) {
        super("Service " + service + " is unavailable. Please try again later.");
    }

    public ExternalServiceUnavailableException(String service, Throwable cause) {
        super("Service " + service + " is unavailable. Please try again later.", cause);
    }
}
