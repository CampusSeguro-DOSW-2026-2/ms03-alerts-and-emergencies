package edu.eci.dosw.campusseguro.alertas.repository;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso al catálogo de tipos de alerta, que vive en la tabla {@code alert_type}.
 */
public interface AlertTypeRepository extends JpaRepository<AlertTypeEntity, String> {

    /**
     * Devuelve todas las filas del catálogo, activas e inactivas, en el orden en que se muestran.
     *
     * <p>Las inactivas se conservan para poder mostrar la etiqueta de alertas históricas.
     *
     * @return todas las filas ordenadas por {@code display_order}
     */
    List<AlertTypeEntity> findAllByOrderByDisplayOrderAsc();

    /**
     * Devuelve los tipos activos en el orden en que se muestran.
     *
     * @return los tipos activos ordenados por {@code display_order}
     */
    List<AlertTypeEntity> findAllByActiveTrueOrderByDisplayOrderAsc();

    /**
     * Busca una fila por su código.
     *
     * @param code código del tipo
     * @return la fila, si existe
     */
    Optional<AlertTypeEntity> findByCode(String code);
}
