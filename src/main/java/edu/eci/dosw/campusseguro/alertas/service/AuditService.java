package edu.eci.dosw.campusseguro.alertas.service;

public interface AuditService {

    /**
     * Registra el hecho en el historial local y lo deja en la bandeja de salida hacia MS08.
     *
     * <p>Debe llamarse dentro de la transacción que cambia la alerta: así el hecho y la alerta
     * existen juntos, y un fallo de MS08 nunca deja el cambio sin auditar.
     *
     * @param record hecho auditable
     */
    void record(AuditRecord record);
}
