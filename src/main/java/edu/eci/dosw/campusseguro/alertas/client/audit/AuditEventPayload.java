package edu.eci.dosw.campusseguro.alertas.client.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Cuerpo de {@code POST /api/v1/audit/events} de MS08 (Estándares §12).
 *
 * <p>MS08 rechaza los campos desconocidos y los nulos viajan omitidos, así que este record es la
 * única forma del contrato: no agregar campos que MS08 no declare.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditEventPayload(
        String id,
        @JsonProperty("usuario_id") String usuarioId,
        String modulo,
        String accion,
        @JsonProperty("recurso_afectado") String recursoAfectado,
        String resultado,
        @JsonProperty("fecha_hora") String fechaHora,
        @JsonProperty("correlation_id") String correlationId,
        String detalle,
        @JsonProperty("evento_id") String eventoId,
        @JsonProperty("tipo_evento") String tipoEvento,
        Map<String, Object> atributos) {
}
