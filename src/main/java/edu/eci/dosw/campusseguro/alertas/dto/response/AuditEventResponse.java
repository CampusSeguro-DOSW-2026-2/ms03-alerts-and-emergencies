package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID alertId,
        String actor,
        String actorType,
        AuditAction action,
        AlertStatus fromStatus,
        AlertStatus toStatus,
        String detail,
        Instant occurredAt) {
}
