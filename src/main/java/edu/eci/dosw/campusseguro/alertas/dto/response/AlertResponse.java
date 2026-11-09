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
        List<AlertType> types,
        String title,
        AlertStatus status,
        AlertOrigin origin,
        UUID simulationId,
        String purpose,
        ScopeRequest scope,
        List<ScopeRequest> scopes,
        String instructions,
        List<RecipientRequest> recipients,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant activationAt,
        Instant activatedAt,
        Instant archivedAt
) {

    /** Keeps the previous constructor for callers that do not use purpose or scopes. */
    public AlertResponse(
            UUID id,
            List<AlertType> types,
            String title,
            AlertStatus status,
            AlertOrigin origin,
            UUID simulationId,
            ScopeRequest scope,
            String instructions,
            List<RecipientRequest> recipients,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            Instant activationAt,
            Instant activatedAt,
            Instant archivedAt) {
        this(id, types, title, status, origin, simulationId, null, scope,
                scope == null ? List.of() : List.of(scope),
                instructions, recipients, createdBy, createdAt, updatedAt, activationAt, activatedAt, archivedAt);
    }
}
