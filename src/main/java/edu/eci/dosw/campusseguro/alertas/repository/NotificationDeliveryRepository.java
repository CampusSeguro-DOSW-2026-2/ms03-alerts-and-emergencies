package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    boolean existsByAlertIdAndUserId(UUID alertId, String userId);
}
