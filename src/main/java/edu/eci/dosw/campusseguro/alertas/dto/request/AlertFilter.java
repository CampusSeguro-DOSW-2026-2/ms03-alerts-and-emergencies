
package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.LocalDate;
import java.util.UUID;

public record AlertFilter(
        AlertStatus status,
        AlertType type,
        LocalDate startDate,
        LocalDate endDate,
        UUID eventId,
        Integer page,
        Integer size
) {
}
