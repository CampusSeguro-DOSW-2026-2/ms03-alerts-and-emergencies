package edu.eci.dosw.campusseguro.alertas.client.audit;

/**
 * MS08 no pudo recibir el hecho por una causa transitoria (5xx, timeout o error de red), así que
 * el despachador lo reintenta con backoff.
 */
public class AuditDeliveryException extends RuntimeException {

    public AuditDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
