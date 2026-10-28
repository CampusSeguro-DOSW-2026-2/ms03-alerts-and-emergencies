package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import java.util.UUID;

public interface AlertLifecycleService {

    AlertResponse schedule(UUID id, AlertScheduleRequest request, String actor);

    default AlertResponse activate(UUID id, String actor) {
        return activate(id, actor, CorrelationIds.current());
    }

    AlertResponse activate(UUID id, String actor, String correlationId);

    void activateScheduledAlerts();

    AlertResponse end(
            UUID id,
            AlertEndRequest request,
            RequesterContext requester);

    AlertResponse archive(UUID id, String actor);
}
