
package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class AlertNotFoundException extends RuntimeException {

    public AlertNotFoundException(UUID id) {
        super("No se encontró la alerta con ID: " + id);
    }
}
