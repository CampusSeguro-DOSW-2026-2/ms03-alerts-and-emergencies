package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertRequestException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catálogo de tipos de alerta, leído de la tabla {@code alert_type}.
 *
 * <p>Los tipos son datos y el administrador puede agregar uno sin desplegar, por eso el catálogo se
 * mantiene en memoria y se refresca cada cierto tiempo en lugar de consultarse en cada petición: una
 * página de veinte alertas consultaría la tabla veinte veces.
 *
 * <p>El código {@value #OPERATIONAL} conserva un significado de negocio: no se puede combinar con otros
 * tipos ni usar en un simulacro.
 */
@Component
public class AlertTypeCatalog {

    /** Código del tipo operativo, el único con reglas propias de negocio. */
    public static final String OPERATIONAL = "OPERATIVA";

    private final AlertTypeRepository repository;

    private final AtomicReference<Map<String, AlertTypeEntity>> snapshot =
            new AtomicReference<>(Map.of());

    public AlertTypeCatalog(AlertTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * Recarga el catálogo desde la base de datos.
     */
    @Scheduled(
            fixedDelayString = "${app.alerts.catalog-refresh-ms:60000}",
            initialDelayString = "${app.alerts.catalog-refresh-ms:60000}")
    public void refresh() {
        Map<String, AlertTypeEntity> loaded = new LinkedHashMap<>();
        for (AlertTypeEntity type : repository.findAllByActiveTrueOrderByDisplayOrderAsc()) {
            loaded.put(type.getCode(), type);
        }
        snapshot.set(Map.copyOf(loaded));
    }

    /**
     * Indica si el código corresponde a un tipo activo del catálogo.
     *
     * @param code código a comprobar, puede ser null
     * @return true si el catálogo lo tiene activo
     */
    public boolean isKnown(String code) {
        return code != null && current().containsKey(code);
    }

    /**
     * Valida que todos los códigos existan en el catálogo.
     *
     * @param codes códigos a comprobar
     * @throws InvalidAlertRequestException si algún código no existe en el catálogo
     */
    public void requireKnown(Collection<String> codes) {
        List<String> unknown = new ArrayList<>();
        for (String code : codes) {
            if (!isKnown(code) && !unknown.contains(code)) {
                unknown.add(code);
            }
        }
        if (!unknown.isEmpty()) {
            throw new InvalidAlertRequestException(
                    "types",
                    "Unknown alert types: " + String.join(", ", unknown));
        }
    }

    /**
     * Ordena los códigos como los muestra el catálogo: por orden de presentación y, si el código
     * desapareció del catálogo, al final y en orden alfabético.
     *
     * @param codes códigos a ordenar
     * @return lista nueva sin repetidos, ordenada
     */
    public List<String> sorted(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, AlertTypeEntity> types = current();
        return codes.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator
                        .comparingInt((String code) -> {
                            AlertTypeEntity type = types.get(code);
                            return type == null ? Integer.MAX_VALUE : type.getDisplayOrder();
                        })
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    /**
     * Devuelve la etiqueta legible de un tipo.
     *
     * @param code código del tipo
     * @return la etiqueta del catálogo, o el código si ya no existe
     */
    public String labelOf(String code) {
        AlertTypeEntity type = current().get(code);
        return type == null ? code : type.getLabel();
    }

    /**
     * Devuelve los códigos activos del catálogo en orden de presentación.
     *
     * @return los códigos disponibles
     */
    public Set<String> codes() {
        return new LinkedHashSet<>(sorted(current().keySet()));
    }

    private Map<String, AlertTypeEntity> current() {
        Map<String, AlertTypeEntity> types = snapshot.get();
        if (types.isEmpty()) {
            refresh();
            types = snapshot.get();
        }
        return types;
    }
}
