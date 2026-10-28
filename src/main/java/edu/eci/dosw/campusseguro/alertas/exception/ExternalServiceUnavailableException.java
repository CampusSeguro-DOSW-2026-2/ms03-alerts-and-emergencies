
package edu.eci.dosw.campusseguro.alertas.exception;

public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String service) {
        super("El servicio " + service
                + " no está disponible. Intenta de nuevo más tarde");
    }

    public ExternalServiceUnavailableException(String service, Throwable cause) {
        super("El servicio " + service
                + " no está disponible. Intenta de nuevo más tarde", cause);
    }
}
