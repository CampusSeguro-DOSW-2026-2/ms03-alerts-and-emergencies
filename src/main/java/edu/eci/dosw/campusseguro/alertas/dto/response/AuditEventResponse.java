package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditStatus;
import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID alertId,
        String actor,
        String actorType,
        AuditAction action,
        AuditStatus fromStatus,
        AuditStatus toStatus,
        String detail,
        Instant occurredAt) {
}
