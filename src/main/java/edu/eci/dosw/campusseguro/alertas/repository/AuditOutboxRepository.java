package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AuditOutboxEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AuditOutboxRepository extends Repository<AuditOutboxEntity, UUID> {

    AuditOutboxEntity save(AuditOutboxEntity entity);

    Optional<AuditOutboxEntity> findById(UUID id);

    /**
     * Toma los pendientes vencidos bloqueándolos, para que dos despachadores no envíen el mismo
     * hecho: el segundo espera y recibe otra fila.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from AuditOutboxEntity o
            where o.estado = :estado and o.proximoIntento <= :now
            order by o.proximoIntento asc
            """)
    List<AuditOutboxEntity> findDue(
            @Param("estado") String estado,
            @Param("now") Instant now,
            Pageable pageable);

    /** Lotes que quedaron tomados por un reinicio del proceso y hay que devolver a pendientes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select o from AuditOutboxEntity o
            where o.estado = :estado and o.updatedAt <= :threshold
            order by o.updatedAt asc
            """)
    List<AuditOutboxEntity> findStuck(
            @Param("estado") String estado,
            @Param("threshold") Instant threshold,
            Pageable pageable);
}
