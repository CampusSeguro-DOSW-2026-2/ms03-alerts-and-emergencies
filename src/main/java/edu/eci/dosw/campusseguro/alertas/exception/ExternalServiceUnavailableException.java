package edu.eci.dosw.campusseguro.alertas.exception;

public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String service) {
        super("El servicio " + service + " no está disponible, intente de nuevo");
    }

    public ExternalServiceUnavailableException(String service, Throwable cause) {
        super("El servicio " + service + " no está disponible, intente de nuevo", cause);
    }
}
