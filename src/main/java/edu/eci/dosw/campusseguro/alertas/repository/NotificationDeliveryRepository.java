package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    boolean existsByAlertIdAndUserId(UUID alertId, String userId);

    Optional<NotificationDeliveryEntity> findByAlertIdAndUserId(UUID alertId, String userId);

    List<NotificationDeliveryEntity> findByAlertId(UUID alertId);

    long countBySentAtGreaterThanEqual(Instant since);
}
