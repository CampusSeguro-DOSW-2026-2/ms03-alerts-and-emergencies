package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID>, JpaSpecificationExecutor<AlertEntity> {

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from AlertEntity a where a.id = :id")
    Optional<AlertEntity> findForUpdate(@Param("id") UUID id);

    List<AlertEntity> findByStatusAndActivationAtLessThanEqual(AlertStatus status, Instant activationAt);

    @Query("select a.status from AlertEntity a where a.id = :id")
    Optional<AlertStatus> findStatusById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AlertEntity a set a.status = :activeStatus, a.activatedAt = :now, a.updatedAt = :now, "
            + "a.version = a.version + 1 where a.id = :id and a.status in :sourceStatuses")
    int activateIfPending(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("activeStatus") AlertStatus activeStatus,
            @Param("sourceStatuses") List<AlertStatus> sourceStatuses);
}

