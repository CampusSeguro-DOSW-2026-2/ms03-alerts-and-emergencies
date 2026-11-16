package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AlertSummaryResponse(
        UUID id,
        List<String> types,
        String title,
        String purpose,
        ScopeRequest scope,
        List<ScopeRequest> scopes,
        String status,
        AlertOrigin origin,
        UUID simulationId,
        Instant createdAt,
        String instructions,
        List<RecipientRequest> recipients
) {

    /** Keeps the previous constructor for callers that do not use purpose or scopes. */
    public AlertSummaryResponse(
            UUID id,
            List<String> types,
            String title,
            ScopeRequest scope,
            String status,
            AlertOrigin origin,
            UUID simulationId,
            Instant createdAt,
            String instructions,
            List<RecipientRequest> recipients) {
        this(id, types, title, null, scope, scope == null ? List.of() : List.of(scope),
                status, origin, simulationId, createdAt, instructions, recipients);
    }
}
