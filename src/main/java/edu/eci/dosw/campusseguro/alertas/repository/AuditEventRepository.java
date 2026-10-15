package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AuditEventRepository extends Repository<AuditEventEntity, UUID> {

    AuditEventEntity save(AuditEventEntity event);
}
