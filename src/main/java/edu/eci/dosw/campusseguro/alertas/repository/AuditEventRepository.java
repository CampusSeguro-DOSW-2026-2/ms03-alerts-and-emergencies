package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AuditEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface AuditEventRepository extends Repository<AuditEventEntity, UUID> {

    AuditEventEntity save(AuditEventEntity event);

    List<AuditEventEntity> findByAlertIdOrderByCreatedAtAsc(UUID alertId);
}
