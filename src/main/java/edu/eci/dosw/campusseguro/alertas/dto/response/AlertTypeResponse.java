package edu.eci.dosw.campusseguro.alertas.dto.response;

/**
 * Tipo de alerta activo expuesto por MS03.
 *
 * @param code código canónico
 * @param label etiqueta visible
 * @param displayOrder orden de presentación
 */
public record AlertTypeResponse(String code, String label, int displayOrder) {
}
