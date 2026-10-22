package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class DuplicateEventAlertException extends RuntimeException {

    public DuplicateEventAlertException(UUID eventId, UUID existingAlertId) {
        super("A live alert already exists for event " + eventId
                + " with different data: " + existingAlertId);
    }
}