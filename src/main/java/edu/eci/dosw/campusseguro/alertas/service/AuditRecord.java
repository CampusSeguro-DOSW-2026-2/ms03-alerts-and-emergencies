package edu.eci.dosw.campusseguro.alertas.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hecho auditable de una alerta.
 *
 * <p>Reúne lo que MS03 guarda en su propio historial ({@code alertId}, {@code action},
 * {@code actor}, estados y {@code details}) y lo que MS08 necesita para sus indicadores
 * ({@code usuarioId}, {@code eventoId}, {@code tipoEvento}, {@code resultado} y
 * {@code atributos}).
 *
 * <p>El {@code eventoId} es el {@code simulationId} de la alerta: MS06 lo envía como el id del
 * evento, así que es el mismo valor con el que MS08 agrupa los hechos de un simulacro.
 *
 * @param usuarioId UUID del usuario que ejecuta la acción, o {@code null} para un sistema o un
 *                  servicio
 * @param atributos datos estructurados que MS08 usa en los indicadores; se envían sin valores
 *                  nulos
 */
public record AuditRecord(
        UUID alertId,
        String action,
        String actor,
        String fromStatus,
        String toStatus,
        String details,
        String usuarioId,
        UUID eventoId,
        AuditEventKind tipoEvento,
        AuditResult resultado,
        Map<String, Object> atributos) {

    public AuditRecord {
        atributos = copyWithoutNulls(atributos);
    }

    public static Builder builder(UUID alertId, String action, String actor) {
        return new Builder(alertId, action, actor);
    }

    /**
     * Devuelve el actor cuando es un UUID, o {@code null} cuando es un sistema
     * ({@code SYSTEM_SCHEDULER}, {@code SYSTEM_NOTIFIER}) o un servicio ({@code ms06:<uuid>}).
     *
     * @param actor actor de auditoría tal como lo compone MS03
     * @return el UUID del usuario, o {@code null}
     */
    public static String userIdOrNull(String actor) {
        if (actor == null) {
            return null;
        }

        try {
            return UUID.fromString(actor.strip()).toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Map<String, Object> copyWithoutNulls(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });

        return Collections.unmodifiableMap(copy);
    }

    /**
     * Arma un hecho auditable. El tipo de evento se deriva del id: con id es un simulacro y sin
     * id es un evento real, de modo que nunca se envía {@code SIMULACRO} sin {@code evento_id}.
     */
    public static final class Builder {

        private final UUID alertId;
        private final String action;
        private final String actor;
        private String fromStatus;
        private String toStatus;
        private String details;
        private String usuarioId;
        private UUID eventoId;
        private AuditResult resultado = AuditResult.EXITO;
        private Map<String, Object> atributos = Map.of();

        private Builder(UUID alertId, String action, String actor) {
            this.alertId = alertId;
            this.action = action;
            this.actor = actor;
        }

        public Builder transition(String fromStatus, String toStatus) {
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Builder usuarioId(String usuarioId) {
            this.usuarioId = usuarioId;
            return this;
        }

        public Builder eventoId(UUID eventoId) {
            this.eventoId = eventoId;
            return this;
        }

        public Builder resultado(AuditResult resultado) {
            this.resultado = resultado;
            return this;
        }

        public Builder atributos(Map<String, Object> atributos) {
            this.atributos = atributos;
            return this;
        }

        public Builder atributo(String key, Object value) {
            Map<String, Object> merged = new LinkedHashMap<>(atributos);
            merged.put(key, value);
            this.atributos = merged;
            return this;
        }

        public AuditRecord build() {
            AuditEventKind tipoEvento =
                    eventoId == null ? AuditEventKind.REAL : AuditEventKind.SIMULACRO;

            return new AuditRecord(
                    alertId,
                    action,
                    actor,
                    fromStatus,
                    toStatus,
                    details,
                    usuarioId,
                    eventoId,
                    tipoEvento,
                    resultado,
                    atributos);
        }
    }
}
