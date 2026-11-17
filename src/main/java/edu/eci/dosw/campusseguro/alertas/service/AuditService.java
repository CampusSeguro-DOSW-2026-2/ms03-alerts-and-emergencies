package edu.eci.dosw.campusseguro.alertas.service;

import java.util.UUID;

public interface AuditService {

    void record(UUID alertId, String action, String actor, String fromStatus, String toStatus, String details);
}
