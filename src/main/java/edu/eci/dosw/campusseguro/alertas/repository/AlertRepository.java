package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AlertRepository
        extends JpaRepository<AlertEntity, UUID>,
                JpaSpecificationExecutor<AlertEntity> {

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from AlertEntity a where a.id = :id")
    Optional<AlertEntity> findForUpdate(@Param("id") UUID id);

    List<AlertEntity> findByStatusAndActivationAtLessThanEqual(
            AlertStatus status,
            Instant activationAt);

    Optional<AlertEntity> findBySimulationIdAndStatusIn(UUID simulationId, Collection<AlertStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AlertEntity a
               set a.status = :targetStatus,
                   a.activatedAt = :now,
                   a.updatedAt = :now,
                   a.version = a.version + 1
             where a.id = :id
               and a.status in :fromStatuses
            """)
    int activateIfPending(
            @Param("id") UUID id,
            @Param("targetStatus") AlertStatus targetStatus,
            @Param("now") Instant now,
            @Param("fromStatuses") Collection<AlertStatus> fromStatuses);

    @Query("""
            select a
              from AlertEntity a
             where a.status in :statuses
               and (
                    exists (
                        select r.id
                          from AlertRecipientEntity r
                         where r.alertId = a.id
                           and r.recipientType = :userType
                           and r.recipientRefId = :userId
                    )
                    or exists (
                        select r.id
                          from AlertRecipientEntity r
                         where r.alertId = a.id
                           and r.recipientType = :roleType
                           and r.recipientRefId in :roles
                    )
                    or exists (
                        select d.id
                          from NotificationDeliveryEntity d
                         where d.alertId = a.id
                           and d.userId = :userId
                    )
               )
            """)
    Page<AlertEntity> findInbox(
            @Param("userId") String userId,
            @Param("roles") Set<String> roles,
            @Param("statuses") Collection<AlertStatus> statuses,
            @Param("userType") RecipientType userType,
            @Param("roleType") RecipientType roleType,
            Pageable pageable);
}