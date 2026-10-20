
package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import java.util.UUID;

public interface AlertService {

    AlertCreationResult create(AlertCreateRequest request, String actor);

    AlertResponse update(UUID id, AlertUpdateRequest request, String actor);

    AlertResponse findById(UUID id, AccessActor actor);

    PageResponse<AlertSummaryResponse> search(AlertFilter filter);
}
