package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class NotARecipientException extends RuntimeException {

    public NotARecipientException(UUID alertId) {
        super("No es destinatario de la alerta " + alertId);
    }
}
