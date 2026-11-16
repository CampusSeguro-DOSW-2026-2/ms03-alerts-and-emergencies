package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID alertId,
        String actor,
        String actorType,
        String action,
        String fromStatus,
        String toStatus,
        String detail,
        Instant occurredAt) {
}
