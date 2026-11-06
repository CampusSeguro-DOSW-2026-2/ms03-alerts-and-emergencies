package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import java.util.List;
import java.util.UUID;

public interface AlertLifecycleService {

    AlertResponse schedule(
            UUID id,
            AlertScheduleRequest request,
            RequesterContext requester);

    default AlertResponse activate(UUID id, RequesterContext requester) {
        return activate(id, requester, CorrelationIds.current());
    }

    AlertResponse activate(
            UUID id,
            RequesterContext requester,
            String correlationId);

    List<UUID> findDueScheduledAlertIds();

    AlertResponse archive(UUID id, RequesterContext requester);
}
