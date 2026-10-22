package edu.eci.dosw.campusseguro.alertas.exception;

import java.util.UUID;

public class EventMismatchException extends RuntimeException {

    public EventMismatchException(UUID alertId, UUID eventId) {
        super("Alert " + alertId + " does not correspond to event " + eventId);
    }
}