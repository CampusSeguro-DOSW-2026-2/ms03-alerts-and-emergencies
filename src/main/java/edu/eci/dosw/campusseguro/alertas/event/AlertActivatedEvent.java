package edu.eci.dosw.campusseguro.alertas.event;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.time.Instant;
import java.util.UUID;

public record AlertActivatedEvent(
        UUID alertId,
        AlertStatus previousStatus,
        Instant activatedAt) {
}
