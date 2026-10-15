package edu.eci.dosw.campusseguro.alertas.dto.response;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.time.Instant;
import java.util.UUID;

public record StateChangeResponse(
        UUID id,
        AlertStatus estado,
        Instant fechaActivacionProgramada) {
}