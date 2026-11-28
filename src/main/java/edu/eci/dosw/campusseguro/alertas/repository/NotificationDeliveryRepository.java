package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.DeliveryStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity, UUID> {

    boolean existsByAlertIdAndUserId(UUID alertId, String userId);

    Optional<NotificationDeliveryEntity> findByAlertIdAndUserId(UUID alertId, String userId);

    List<NotificationDeliveryEntity> findByAlertId(UUID alertId);

    long countBySentAtGreaterThanEqual(Instant since);

    /**
     * Alertas activas, activadas desde {@code since}, con alguna entrega fallida que no se toca desde
     * {@code before} y cuyo motivo no es {@code skipReason}.
     */
    @Query("""
            select distinct d.alertId
            from NotificationDeliveryEntity d, AlertEntity a
            where a.id = d.alertId
              and a.status = 'ACTIVE'
              and a.activatedAt >= :since
              and d.status = :status
              and d.updatedAt <= :before
              and (d.errorReason is null or d.errorReason <> :skipReason)
            """)
    List<UUID> findAlertIdsWithDeliveriesToRetry(
            @Param("status") DeliveryStatus status,
            @Param("since") Instant since,
            @Param("before") Instant before,
            @Param("skipReason") String skipReason);
}
