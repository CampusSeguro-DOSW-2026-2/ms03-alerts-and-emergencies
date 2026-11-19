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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Catálogo de tipos de alerta, leído de la tabla {@code alert_type}.
 *
 * <p>MS06 es la fuente de verdad y MS03 conserva una réplica local. El catálogo se mantiene en
 * memoria para no consultar la tabla por cada alerta de una página, se sincroniza con MS06 cada
 * cierto tiempo y se recarga cuando aparece un código desconocido. Si MS06 no responde, se conserva
 * la última copia y el servicio sigue validando y sirviendo el catálogo.
 *
 * <p>El código {@value #OPERATIONAL} conserva un significado de negocio: no se puede combinar con
 * otros tipos ni usar en un simulacro.
 */
@Component
public class AlertTypeCatalog {

    /** Código del tipo operativo, el único con reglas propias de negocio. */
    public static final String OPERATIONAL = "OPERATIVA";

    private static final Logger log = LoggerFactory.getLogger(AlertTypeCatalog.class);

    private final AlertTypeRepository repository;

    private final AlertTypeSynchronizer synchronizer;

    private final long refreshThrottleMs;

    private final AtomicReference<Map<String, AlertTypeEntity>> snapshot =
            new AtomicReference<>(Map.of());

    private final AtomicBoolean synchronizing = new AtomicBoolean(false);

    private final AtomicLong lastRefreshAttempt = new AtomicLong(0L);

    @Autowired
    public AlertTypeCatalog(
            AlertTypeRepository repository,
            AlertTypeSynchronizer synchronizer,
            @Value("${app.alerts.types.refresh-throttle-ms:5000}") long refreshThrottleMs) {

        this.repository = repository;
        this.synchronizer = synchronizer;
        this.refreshThrottleMs = Math.max(0L, refreshThrottleMs);
    }

    public AlertTypeCatalog(AlertTypeRepository repository, AlertTypeSynchronizer synchronizer) {
        this(repository, synchronizer, 5000L);
    }

    /**
     * Sincroniza con MS06 y recarga el catálogo. Si MS06 falla, registra el error y conserva la
     * última copia local.
     */
    @Scheduled(
            fixedDelayString = "${app.alerts.types.sync-ms:60000}",
            initialDelayString = "${app.alerts.types.sync-initial-delay-ms:5000}")
    public void synchronizeAndReload() {
        synchronizeOnce();
    }

    /**
     * Recarga el catálogo desde la base de datos, incluidas las filas inactivas que se conservan
     * para mostrar la etiqueta de alertas históricas.
     */
    public void load() {
        Map<String, AlertTypeEntity> loaded = new LinkedHashMap<>();
        List<AlertTypeEntity> rows = repository.findAllByOrderByDisplayOrderAsc();
        if (rows != null) {
            for (AlertTypeEntity type : rows) {
                if (type != null) {
                    loaded.put(type.getCode(), type);
                }
            }
        }
        snapshot.set(Map.copyOf(loaded));
    }

    /**
     * Recarga el catálogo sin sincronizar. Se conserva por compatibilidad con los llamadores que
     * solo necesitan releer la base.
     */
    public void refresh() {
        load();
    }

    /**
     * Indica si el código corresponde a un tipo activo del catálogo.
     *
     * @param code código a comprobar, puede ser null
     * @return true si el catálogo lo tiene activo
     */
    public boolean isKnown(String code) {
        if (code == null) {
            return false;
        }
        AlertTypeEntity type = current().get(code);
        return type != null && type.isActive();
    }

    /**
     * Valida que todos los códigos existan y estén activos en el catálogo.
     *
     * <p>Si aparece un código desconocido, fuerza como máximo una sincronización cada
     * {@code app.alerts.types.refresh-throttle-ms} milisegundos y reintenta la validación.
     *
     * @param codes códigos a comprobar
     * @throws InvalidAlertRequestException si algún código sigue desconocido tras el reintento
     */
    public void requireKnown(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return;
        }

        List<String> unknown = unknownCodes(codes);
        if (unknown.isEmpty()) {
            return;
        }

        refreshOnce();

        unknown = unknownCodes(codes);
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
     * Devuelve la etiqueta legible de un tipo, aunque la fila esté inactiva.
     *
     * @param code código del tipo
     * @return la etiqueta del catálogo, o el código si ya no existe
     */
    public String labelOf(String code) {
        AlertTypeEntity type = current().get(code);
        return type == null ? code : type.getLabel();
    }

    /**
     * Devuelve las filas activas del catálogo en orden de presentación.
     *
     * @return tipos activos ordenados
     */
    public List<AlertTypeEntity> active() {
        return current().values().stream()
                .filter(AlertTypeEntity::isActive)
                .sorted(Comparator
                        .comparingInt(AlertTypeEntity::getDisplayOrder)
                        .thenComparing(AlertTypeEntity::getCode))
                .toList();
    }

    /**
     * Devuelve los códigos activos del catálogo en orden de presentación.
     *
     * @return los códigos disponibles
     */
    public Set<String> codes() {
        Set<String> activeCodes = new LinkedHashSet<>();
        for (AlertTypeEntity type : active()) {
            activeCodes.add(type.getCode());
        }
        return activeCodes;
    }

    private List<String> unknownCodes(Collection<String> codes) {
        List<String> unknown = new ArrayList<>();
        for (String code : codes) {
            if (!isKnown(code) && !unknown.contains(code)) {
                unknown.add(code);
            }
        }
        return unknown;
    }

    private void refreshOnce() {
        long now = System.currentTimeMillis();
        long previous = lastRefreshAttempt.get();
        if (now - previous < refreshThrottleMs) {
            return;
        }
        if (!lastRefreshAttempt.compareAndSet(previous, now)) {
            return;
        }
        synchronizeOnce();
    }

    /**
     * Ejecuta a lo sumo una sincronización a la vez. Un fallo de MS06 se registra y deja intacto el
     * snapshot actual.
     *
     * @return true si la sincronización pudo ejecutarse
     */
    private boolean synchronizeOnce() {
        if (!synchronizing.compareAndSet(false, true)) {
            return false;
        }
        try {
            boolean applied = synchronizer.synchronize();
            load();
            return applied;
        } catch (RuntimeException ex) {
            log.warn("No se pudo sincronizar el catalogo de tipos de alerta con MS06; "
                    + "se conserva la copia local", ex);
            return false;
        } finally {
            synchronizing.set(false);
        }
    }

    private Map<String, AlertTypeEntity> current() {
        Map<String, AlertTypeEntity> types = snapshot.get();
        if (types.isEmpty()) {
            load();
            types = snapshot.get();
        }
        return types;
    }
}
