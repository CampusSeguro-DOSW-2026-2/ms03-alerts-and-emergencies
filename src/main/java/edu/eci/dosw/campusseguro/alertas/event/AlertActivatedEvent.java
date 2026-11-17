package edu.eci.dosw.campusseguro.alertas.event;

import java.time.Instant;
import java.util.UUID;

public record AlertActivatedEvent(
        UUID alertId,
        String previousStatus,
        Instant activatedAt,
        String correlationId) {
}
