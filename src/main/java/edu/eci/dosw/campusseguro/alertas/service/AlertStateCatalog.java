package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.exception.InvalidTransitionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catálogo de estados del ciclo de vida y de sus transiciones, leído de las tablas
 * {@code alert_status} y {@code alert_status_transition}.
 *
 * <p>Agregar un estado o una transición es insertar filas. La máquina de estados ya no tiene un mapa
 * fijo en el código: pregunta aquí. Igual que el catálogo de tipos, se mantiene en memoria y se refresca
 * cada cierto tiempo para no consultar la base en cada operación.
 */
@Component
public class AlertStateCatalog {

    private final JdbcTemplate jdbcTemplate;

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.empty());

    public AlertStateCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Vuelve a leer los estados y sus transiciones.
     */
    @Scheduled(
            fixedDelayString = "${app.alerts.catalog-refresh-ms:60000}",
            initialDelayString = "${app.alerts.catalog-refresh-ms:60000}")
    public void refresh() {
        Map<String, StatusRow> statuses = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT code, label, display_order, editable, final_state "
                        + "FROM alert_status ORDER BY display_order",
                resultSet -> {
                    StatusRow row = new StatusRow(
                            resultSet.getString("code"),
                            resultSet.getString("label"),
                            resultSet.getInt("display_order"),
                            resultSet.getBoolean("editable"),
                            resultSet.getBoolean("final_state"));
                    statuses.put(row.code(), row);
                });

        Map<String, Set<String>> transitions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT from_code, to_code FROM alert_status_transition",
                (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> transitions
                        .computeIfAbsent(
                                resultSet.getString("from_code"),
                                key -> new LinkedHashSet<>())
                        .add(resultSet.getString("to_code")));


        snapshot.set(new Snapshot(
                Map.copyOf(statuses),
                Map.copyOf(transitions)));
    }

    /**
     * Indica si el código existe en el catálogo de estados.
     *
     * @param code código del estado, puede ser null
     * @return true si el estado existe
     */
    public boolean isKnown(String code) {
        return code != null && current().statuses().containsKey(code);
    }

    /**
     * Indica si una alerta en ese estado se puede editar.
     *
     * @param code código del estado
     * @return true si el catálogo lo marca editable
     */
    public boolean isEditable(String code) {
        StatusRow row = current().statuses().get(code);
        return row != null && row.editable();
    }

    /**
     * Indica si el estado es terminal, es decir, si ya no admite operaciones.
     *
     * @param code código del estado
     * @return true si el catálogo lo marca como terminal
     */
    public boolean isFinal(String code) {
        StatusRow row = current().statuses().get(code);
        return row != null && row.finalState();
    }

    /**
     * Indica si la transición está declarada en el catálogo.
     *
     * @param from estado actual
     * @param to estado solicitado
     * @return true si la transición existe
     */
    public boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return current().transitions()
                .getOrDefault(from, Set.of())
                .contains(to);
    }

    /**
     * Comprueba una transición y falla si el catálogo no la declara.
     *
     * @param from estado actual
     * @param to estado solicitado
     * @throws InvalidTransitionException si la transición no está declarada
     */
    public void assertTransition(String from, String to) {
        if (!canTransition(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }

    /**
     * Devuelve los códigos en el orden en que se muestran.
     *
     * @param codes códigos a ordenar
     * @return lista nueva sin repetidos, ordenada
     */
    public List<String> sorted(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, StatusRow> statuses = current().statuses();
        return codes.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator
                        .comparingInt((String code) -> {
                            StatusRow row = statuses.get(code);
                            return row == null ? Integer.MAX_VALUE : row.displayOrder();
                        })
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * Devuelve la etiqueta legible de un estado.
     *
     * @param code código del estado
     * @return la etiqueta del catálogo, o el código si no existe
     */
    public String labelOf(String code) {
        StatusRow row = current().statuses().get(code);
        return row == null ? code : row.label();
    }

    /**
     * Devuelve los estados que admiten al menos una transición desde el actual.
     *
     * @param from estado actual
     * @return los códigos alcanzables
     */
    public Set<String> reachableFrom(String from) {
        return current().transitions().getOrDefault(from, Set.of());
    }

    private Snapshot current() {
        Snapshot value = snapshot.get();
        if (value.statuses().isEmpty()) {
            refresh();
            value = snapshot.get();
        }
        return value;
    }

    private record StatusRow(
            String code,
            String label,
            int displayOrder,
            boolean editable,
            boolean finalState) {
    }

    private record Snapshot(
            Map<String, StatusRow> statuses,
            Map<String, Set<String>> transitions) {

        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of());
        }
    }

    /**
     * Devuelve los códigos del catálogo en orden de presentación.
     *
     * @return los códigos disponibles
     */
    public List<String> codes() {
        return new ArrayList<>(current().statuses().keySet());
    }
}
