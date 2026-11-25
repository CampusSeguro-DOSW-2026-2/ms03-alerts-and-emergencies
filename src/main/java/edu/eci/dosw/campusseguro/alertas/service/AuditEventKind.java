package edu.eci.dosw.campusseguro.alertas.service;

/**
 * Evento al que pertenece un hecho auditado, con los valores del contrato de MS08.
 *
 * <p>Un hecho con {@code evento_id} es de un simulacro; uno sin evento es de un evento real
 * (una alerta manual). MS08 exige el tipo cuando llega el id y prohíbe {@code SIMULACRO} sin id.
 */
public enum AuditEventKind {

    SIMULACRO,
    REAL
}
