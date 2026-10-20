
package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        AlertType type,
        AlertStatus status,
        AlertOrigin origin,
        UUID eventId,
        ScopeRequest scope,
        String instructions,
        List<RecipientRequest> recipients,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant activationAt,
        Instant activatedAt,
        Instant endedAt,
        Instant archivedAt
) {
}
