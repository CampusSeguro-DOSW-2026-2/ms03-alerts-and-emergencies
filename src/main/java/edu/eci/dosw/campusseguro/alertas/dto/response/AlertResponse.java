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
        AlertType tipo,
        AlertStatus estado,
        AlertOrigin origen,
        String simulationId,
        ScopeRequest alcance,
        String instrucciones,
        List<RecipientRequest> destinatarios,
        String creadaPor,
        Instant fechaCreacion,
        Instant fechaActualizacion,
        Instant fechaActivacionProgramada,
        Instant fechaActivacion,
        Instant fechaFinalizacion,
        Instant fechaArchivado) {
}
