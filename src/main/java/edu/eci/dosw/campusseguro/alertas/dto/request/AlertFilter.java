package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.time.LocalDate;

public record AlertFilter(
        AlertStatus estado,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String simulationId,
        Integer page,
        Integer size) {
}
