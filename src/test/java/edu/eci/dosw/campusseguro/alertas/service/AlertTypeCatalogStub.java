package edu.eci.dosw.campusseguro.alertas.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.util.List;

/**
 * Catálogo de tipos de alerta para las pruebas, con la misma siembra que la migración V8.
 *
 * <p>En producción el catálogo viene de la tabla {@code alert_type}; en una prueba unitaria se arma con
 * un repositorio simulado para no necesitar base de datos.
 */
public final class AlertTypeCatalogStub {

    private AlertTypeCatalogStub() {
    }

    /**
     * Crea un catálogo ya cargado con los cuatro tipos iniciales.
     *
     * @return el catálogo listo para usar
     */
    public static AlertTypeCatalog create() {
        AlertTypeRepository repository = mock(AlertTypeRepository.class);
        when(repository.findAllByActiveTrueOrderByDisplayOrderAsc()).thenReturn(List.of(
                new AlertTypeEntity("EARTHQUAKE", "Terremoto", 1, true),
                new AlertTypeEntity("FIRE", "Incendio", 2, true),
                new AlertTypeEntity("OTHER", "Otra emergencia", 3, true),
                new AlertTypeEntity("OPERATIVA", "Operativa", 4, true)));

        AlertTypeCatalog catalog = new AlertTypeCatalog(repository);
        catalog.refresh();
        return catalog;
    }
}
