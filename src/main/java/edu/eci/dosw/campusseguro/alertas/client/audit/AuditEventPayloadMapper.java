package edu.eci.dosw.campusseguro.alertas.client.audit;

import edu.eci.dosw.campusseguro.alertas.service.AuditEventKind;
import edu.eci.dosw.campusseguro.alertas.service.AuditRecord;
import edu.eci.dosw.campusseguro.alertas.service.AuditResult;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Traduce un {@link AuditRecord} al cuerpo que espera MS08.
 *
 * <p>El {@code id} es el del evento de auditoría local, así que un reintento del mismo hecho llega
 * con el mismo identificador y MS08 lo ignora como duplicado.
 */
@Component
public class AuditEventPayloadMapper {

    /** Módulo que MS03 declara en el contrato de auditoría. */
    static final String MODULE = "MS03";

    /** MS08 limita el detalle a 100 caracteres; el historial local conserva hasta 500. */
    static final int MAX_DETAIL_LENGTH = 100;

    private static final String RESOURCE_PREFIX = "alerta:";

    public AuditEventPayload toPayload(
            UUID eventId,
            AuditRecord record,
            Instant occurredAt,
            String correlationId) {

        return new AuditEventPayload(
                eventId == null ? null : eventId.toString(),
                record.usuarioId(),
                MODULE,
                record.action(),
                RESOURCE_PREFIX + record.alertId(),
                resultado(record.resultado()),
                occurredAt == null ? null : occurredAt.toString(),
                correlationId,
                truncate(record.details()),
                record.eventoId() == null ? null : record.eventoId().toString(),
                tipoEvento(record.tipoEvento()),
                record.atributos().isEmpty() ? null : record.atributos());
    }

    private String resultado(AuditResult resultado) {
        return (resultado == null ? AuditResult.EXITO : resultado).name();
    }

    private String tipoEvento(AuditEventKind tipoEvento) {
        return tipoEvento == null ? AuditEventKind.REAL.name() : tipoEvento.name();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_DETAIL_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_DETAIL_LENGTH);
    }
}
