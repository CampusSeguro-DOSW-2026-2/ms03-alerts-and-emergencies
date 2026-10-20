
package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.Instant;
import java.util.UUID;

public record AlertSummaryResponse(
        UUID id,
        AlertType type,
        ScopeRequest scope,
        AlertStatus status,
        AlertOrigin origin,
        Instant createdAt
) {
}
