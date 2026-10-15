package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertScopeItemRepository extends JpaRepository<AlertScopeItemEntity, UUID> {

    List<AlertScopeItemEntity> findByAlertId(UUID alertId);

    List<AlertScopeItemEntity> findByAlertIdIn(Collection<UUID> alertIds);
}
