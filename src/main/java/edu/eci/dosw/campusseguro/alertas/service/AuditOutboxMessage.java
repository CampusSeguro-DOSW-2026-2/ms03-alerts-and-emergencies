package edu.eci.dosw.campusseguro.alertas.service;

import java.util.UUID;

/**
 * Un hecho tomado de la bandeja de salida para entregarlo a MS08.
 *
 * @param id       identificador del hecho (el mismo del evento de auditoría local)
 * @param payload  cuerpo JSON ya serializado
 * @param intentos envíos intentados hasta ahora, incluido el actual
 */
public record AuditOutboxMessage(UUID id, String payload, int intentos) {
}
