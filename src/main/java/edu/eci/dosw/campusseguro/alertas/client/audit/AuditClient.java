package edu.eci.dosw.campusseguro.alertas.client.audit;

/**
 * Entrega un hecho de auditoría a MS08.
 *
 * <p>El cuerpo ya viene serializado desde la bandeja de salida, así que lo que se envía es
 * exactamente lo que se guardó.
 */
public interface AuditClient {

    /**
     * Envía el hecho.
     *
     * @param payload cuerpo JSON del hecho
     * @throws AuditRejectedException si MS08 lo rechaza (4xx)
     * @throws AuditDeliveryException si la entrega falla por una causa transitoria
     */
    void send(String payload);
}
