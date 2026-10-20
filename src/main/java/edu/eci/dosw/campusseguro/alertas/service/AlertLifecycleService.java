
package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import java.util.UUID;

public interface AlertLifecycleService {

    StateChangeResponse schedule(UUID id, AlertScheduleRequest request, String actor);

    StateChangeResponse activate(UUID id, String actor);

    void activateScheduledAlerts();

    StateChangeResponse end(UUID id, String actor);

    StateChangeResponse end(UUID id, AlertEndRequest request, String actor);

    StateChangeResponse archive(UUID id, String actor);

}
