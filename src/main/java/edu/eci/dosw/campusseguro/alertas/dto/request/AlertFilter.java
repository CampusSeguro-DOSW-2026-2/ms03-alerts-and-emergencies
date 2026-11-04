package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AlertFilter(
        AlertStatus status,
        List<AlertType> types,
        AlertOrigin origin,
        ScopeType scope,
        Instant activeAt,
        LocalDate startDate,
        LocalDate endDate,
        UUID simulationId,
        Integer page,
        Integer size
) {
}
