package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.util.List;

/**
 * Grupo de filtros del listado de alertas.
 *
 * @param key identificador del grupo, se usa como nombre del parámetro de consulta
 * @param label etiqueta visible del grupo
 * @param multiple true si se pueden seleccionar varias opciones a la vez
 * @param options opciones del grupo en orden de presentación
 */
public record AlertFilterGroupResponse(
        String key,
        String label,
        boolean multiple,
        List<AlertFilterOptionResponse> options) {
}
