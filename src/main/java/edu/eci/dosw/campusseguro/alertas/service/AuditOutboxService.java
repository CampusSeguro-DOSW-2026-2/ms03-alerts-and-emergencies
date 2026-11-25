package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.audit.AuditEventPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bandeja de salida de la auditoría hacia MS08.
 *
 * <p>{@link #enqueue} participa de la transacción de negocio; el resto lo usa el despachador, que
 * no mantiene la transacción abierta mientras hace la llamada HTTP.
 */
public interface AuditOutboxService {

    /**
     * Guarda el hecho para entregarlo después. No hace nada si la integración está apagada.
     *
     * @param payload cuerpo del contrato de MS08
     */
    void enqueue(AuditEventPayload payload);

    /**
     * Toma un lote de hechos pendientes y los marca como en envío.
     *
     * @return los hechos a entregar, o una lista vacía si no hay pendientes vencidos
     */
    List<AuditOutboxMessage> claimDue();

    void markSent(UUID id);

    void markRetry(UUID id, String error, Instant nextAttempt);

    void markDiscarded(UUID id, String error);
}
