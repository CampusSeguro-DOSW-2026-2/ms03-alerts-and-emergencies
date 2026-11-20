package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.util.List;

/**
 * Catálogo de filtros del listado de alertas: los grupos que el frontend debe renderizar.
 *
 * @param groups grupos de filtros en orden de presentación
 */
public record AlertFilterCatalogResponse(List<AlertFilterGroupResponse> groups) {
}
