package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import java.util.List;
import java.util.UUID;

public interface AuditQueryService {

    List<AuditEventResponse> findByAlert(UUID alertId);
}
