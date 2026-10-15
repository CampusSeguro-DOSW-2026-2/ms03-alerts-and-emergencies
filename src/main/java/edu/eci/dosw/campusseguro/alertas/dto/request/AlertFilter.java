package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.time.LocalDate;

public record AlertFilter(
        AlertStatus estado,
        AlertType tipo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String simulationId,
        Integer page,
        Integer size) {
}
