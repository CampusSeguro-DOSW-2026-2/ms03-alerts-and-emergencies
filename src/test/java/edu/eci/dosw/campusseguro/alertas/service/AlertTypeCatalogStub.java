package edu.eci.dosw.campusseguro.alertas.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.util.List;

/**
 * Catálogo de tipos de alerta para las pruebas, con la misma siembra que la migración V11.
 *
 * <p>En producción el catálogo viene de la tabla {@code alert_type}; en una prueba unitaria se arma
 * con un repositorio simulado para no necesitar base de datos. El sincronizador también se simula:
 * las pruebas que necesitan el refresco bajo demanda lo configuran aparte.
 */
public final class AlertTypeCatalogStub {

    private AlertTypeCatalogStub() {
    }

    /**
     * Crea un catálogo ya cargado con los tipos canónicos de MS06 y {@code OPERATIVA}.
     *
     * @return el catálogo listo para usar
     */
    public static AlertTypeCatalog create() {
        AlertTypeRepository repository = mock(AlertTypeRepository.class);
        AlertTypeSynchronizer synchronizer = mock(AlertTypeSynchronizer.class);

        AlertTypeEntity operativa = new AlertTypeEntity(
                AlertTypeCatalog.OPERATIONAL, "Operativa", 1000, true);
        operativa.setSource(AlertTypeEntity.SOURCE_LOCAL);

        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                new AlertTypeEntity("SISMO", "Sismo", 1, true),
                new AlertTypeEntity("INCENDIO", "Incendio", 2, true),
                new AlertTypeEntity("DISTURBIOS", "Disturbios", 3, true),
                new AlertTypeEntity("INUNDACION", "Inundación", 4, true),
                new AlertTypeEntity("FUGA_QUIMICA", "Fuga de Sustancias Químicas", 5, true),
                new AlertTypeEntity("OTRO", "Otro", 6, true),
                operativa));

        AlertTypeCatalog catalog = new AlertTypeCatalog(repository, synchronizer);
        catalog.load();
        return catalog;
    }
}
