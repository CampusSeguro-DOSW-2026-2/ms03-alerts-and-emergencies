package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRecipientRepository extends JpaRepository<AlertRecipientEntity, UUID> {

    List<AlertRecipientEntity> findByAlertId(UUID alertId);
}
