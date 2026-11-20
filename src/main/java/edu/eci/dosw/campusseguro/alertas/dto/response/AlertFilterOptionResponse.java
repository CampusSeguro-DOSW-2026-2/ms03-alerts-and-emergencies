package edu.eci.dosw.campusseguro.alertas.dto.response;

/**
 * Opción de un grupo de filtros del listado de alertas.
 *
 * @param value valor que se envía en la consulta
 * @param label etiqueta visible
 * @param finalState true si la opción es un estado terminal; nulo para grupos que no son de estado
 */
public record AlertFilterOptionResponse(
        String value,
        String label,
        Boolean finalState) {
}
