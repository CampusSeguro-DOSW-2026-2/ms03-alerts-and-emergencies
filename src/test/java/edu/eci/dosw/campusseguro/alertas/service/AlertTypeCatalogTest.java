package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertRequestException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertTypeCatalogTest {

    @Mock
    private AlertTypeRepository repository;

    @Mock
    private AlertTypeSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        // El catálogo carga desde el repositorio simulado; el sincronizador solo
        // se invoca cuando aparece un código desconocido.
    }

    @Test
    void load_shouldKeepInactiveTypesForLabelsButNotForValidation() {
        AlertTypeEntity sismo = type("SISMO", "Sismo", 1, true);
        AlertTypeEntity retirado = type("RETIRADO", "Tipo retirado", 9, false);
        when(repository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(sismo, retirado));

        AlertTypeCatalog catalog = catalog(5000L);
        catalog.load();

        assertTrue(catalog.isKnown("SISMO"));
        assertFalse(catalog.isKnown("RETIRADO"));
        assertEquals("Tipo retirado", catalog.labelOf("RETIRADO"));
        assertTrue(catalog.codes().contains("SISMO"));
        assertFalse(catalog.codes().contains("RETIRADO"));
    }

    @Test
    void requireKnown_shouldSynchronizeAndRetryWhenCodeIsUnknown() {
        AlertTypeEntity sismo = type("SISMO", "Sismo", 1, true);
        AlertTypeEntity nuevo = type("NUEVO", "Tipo nuevo", 7, true);
        when(repository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(sismo))
                .thenReturn(List.of(sismo, nuevo));
        when(synchronizer.synchronize()).thenReturn(true);

        AlertTypeCatalog catalog = catalog(0L);
        catalog.load();

        assertDoesNotThrow(() -> catalog.requireKnown(List.of("NUEVO")));
        verify(synchronizer).synchronize();
    }

    @Test
    void requireKnown_shouldThrottleSynchronizations() {
        AlertTypeEntity sismo = type("SISMO", "Sismo", 1, true);
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(sismo));
        when(synchronizer.synchronize()).thenReturn(false);

        AlertTypeCatalog catalog = catalog(5000L);
        catalog.load();

        assertThrows(
                InvalidAlertRequestException.class,
                () -> catalog.requireKnown(List.of("DESCONOCIDO")));
        assertThrows(
                InvalidAlertRequestException.class,
                () -> catalog.requireKnown(List.of("OTRO_DESCONOCIDO")));

        verify(synchronizer, times(1)).synchronize();
    }

    @Test
    void requireKnown_shouldKeepSnapshotWhenTheSynchronizationFails() {
        AlertTypeEntity sismo = type("SISMO", "Sismo", 1, true);
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(sismo));
        when(synchronizer.synchronize()).thenThrow(new IllegalStateException("MS06 caido"));

        AlertTypeCatalog catalog = catalog(0L);
        catalog.load();

        assertThrows(
                InvalidAlertRequestException.class,
                () -> catalog.requireKnown(List.of("DESCONOCIDO")));
        assertTrue(catalog.isKnown("SISMO"));
    }

    private AlertTypeCatalog catalog(long throttleMs) {
        return new AlertTypeCatalog(repository, synchronizer, throttleMs);
    }

    private AlertTypeEntity type(String code, String label, int displayOrder, boolean active) {
        return new AlertTypeEntity(code, label, displayOrder, active);
    }
}
