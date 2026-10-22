package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import java.util.UUID;

public interface AlertLifecycleService {

    AlertResponse schedule(UUID id, AlertScheduleRequest request, String actor);

    AlertResponse activate(UUID id, String actor);

    void activateScheduledAlerts();

    AlertResponse end(UUID id, AlertEndRequest request, String actor);

    AlertResponse archive(UUID id, String actor);
}