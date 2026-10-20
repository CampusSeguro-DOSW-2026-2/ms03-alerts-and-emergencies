package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertConfirmationRepository extends JpaRepository<AlertConfirmationEntity, UUID> {

    Optional<AlertConfirmationEntity> findByAlertIdAndUserId(UUID alertId, String userId);

    boolean existsByAlertIdAndUserId(UUID alertId, String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO alert_confirmations (id, alert_id, user_id, confirmed_at, created_at, updated_at)
            VALUES (gen_random_uuid(), :alertId, :userId, :now, :now, :now)
            ON CONFLICT (alert_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("alertId") UUID alertId,
            @Param("userId") String userId,
            @Param("now") Instant now);
}
