package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.time.Instant;
import java.util.List;

public interface AlertRepository extends JpaRepository<AlertEntity, UUID>, JpaSpecificationExecutor<AlertEntity> {

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select a from AlertEntity a where a.id = :id")
    Optional<AlertEntity> findForUpdate(@Param("id") UUID id);
    List<AlertEntity> findByStatusAndActivationAtLessThanEqual(
        AlertStatus status, Instant activationAt);
}

