package edu.eci.dosw.campusseguro.alertas.client.audit;

/**
 * MS08 respondió con un 4xx: el hecho no se puede entregar tal como está, así que reintentarlo no
 * lo arregla y queda descartado en la bandeja de salida.
 */
public class AuditRejectedException extends RuntimeException {

    private final int status;

    public AuditRejectedException(int status) {
        super("MS08 rechazo la auditoria con estado " + status);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
