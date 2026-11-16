package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al catálogo de tipos de alerta, que vive en la tabla {@code alert_type}.
 */
public interface AlertTypeRepository extends JpaRepository<AlertTypeEntity, String> {

    /**
     * Devuelve los tipos activos en el orden en que se muestran.
     *
     * @return los tipos activos ordenados por {@code display_order}
     */
    List<AlertTypeEntity> findAllByActiveTrueOrderByDisplayOrderAsc();
}
