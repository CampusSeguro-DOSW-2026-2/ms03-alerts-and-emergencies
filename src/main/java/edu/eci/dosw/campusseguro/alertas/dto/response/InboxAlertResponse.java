package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InboxAlertResponse(
        UUID id,
        List<AlertType> types,
        String title,
        AlertStatus status,
        AlertOrigin origin,
        ScopeRequest scope,
        String instructions,
        Instant activatedAt,
        Instant archivedAt,
        Instant confirmedAt
) {
}
