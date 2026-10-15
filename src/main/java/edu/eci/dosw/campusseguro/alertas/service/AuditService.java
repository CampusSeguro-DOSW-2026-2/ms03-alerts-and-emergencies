package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import java.util.UUID;

public interface AuditService {

    void record(UUID alertId, AuditAction action, String actor, AlertStatus fromStatus, AlertStatus toStatus, String details);
}
