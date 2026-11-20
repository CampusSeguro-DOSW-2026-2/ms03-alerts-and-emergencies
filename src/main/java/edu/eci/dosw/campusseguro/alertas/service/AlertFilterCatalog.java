package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterGroupResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterOptionResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Catálogo de filtros del listado de alertas.
 *
 * <p>Los grupos y sus opciones salen de los catálogos que ya viven en memoria (estados y tipos), así
 * que agregar un estado o un tipo no requiere tocar el frontend ni consultar la base por petición.
 */
@Component
public class AlertFilterCatalog {

    private final AlertStateCatalog alertStateCatalog;

    private final AlertTypeCatalog alertTypeCatalog;

    public AlertFilterCatalog(
            AlertStateCatalog alertStateCatalog,
            AlertTypeCatalog alertTypeCatalog) {
        this.alertStateCatalog = alertStateCatalog;
        this.alertTypeCatalog = alertTypeCatalog;
    }

    /**
     * Devuelve los grupos de filtros en orden de presentación.
     *
     * @return estado (selección única) y tipo (selección múltiple)
     */
    public List<AlertFilterGroupResponse> groups() {
        List<AlertFilterOptionResponse> statusOptions = alertStateCatalog.options().stream()
                .map(option -> new AlertFilterOptionResponse(
                        option.code(),
                        option.label(),
                        option.finalState()))
                .toList();

        List<AlertFilterOptionResponse> typeOptions = alertTypeCatalog.active().stream()
                .map(type -> new AlertFilterOptionResponse(
                        type.getCode(),
                        type.getLabel(),
                        null))
                .toList();

        return List.of(
                new AlertFilterGroupResponse("status", "Estado", false, statusOptions),
                new AlertFilterGroupResponse("type", "Tipo", true, typeOptions));
    }
}
