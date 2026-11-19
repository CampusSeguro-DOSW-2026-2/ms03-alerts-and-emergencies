package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.alerttype.AlertTypeSyncClient;
import edu.eci.dosw.campusseguro.alertas.client.alerttype.RemoteAlertType;
import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.repository.AlertTypeRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertTypeSynchronizerTest {

    @Mock
    private AlertTypeRepository repository;

    @Mock
    private AlertTypeSyncClient client;

    private AlertTypeSynchronizer synchronizer;

    @BeforeEach
    void setUp() {
        synchronizer = new AlertTypeSynchronizer(repository, client);
    }

    @Test
    void synchronize_shouldUpsertRemoteTypesAndDeactivateMissingOnes() {
        AlertTypeEntity sismo = type("SISMO", "Terremoto", 1, true, "MS06");
        AlertTypeEntity incendio = type("INCENDIO", "Incendio", 2, true, "MS06");
        AlertTypeEntity operativa = type("OPERATIVA", "Operativa", 1000, true, "LOCAL");
        when(repository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(sismo, incendio, operativa));
        when(client.fetch()).thenReturn(List.of(
                new RemoteAlertType("SISMO", "Sismo"),
                new RemoteAlertType("DISTURBIOS", "Disturbios")));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = synchronizer.synchronize();

        assertTrue(applied);
        assertEquals("Sismo", sismo.getLabel());
        assertTrue(sismo.isActive());
        assertNotNull(sismo.getSyncedAt());
        assertEquals(1, sismo.getDisplayOrder());

        assertFalse(incendio.isActive());

        assertEquals("Operativa", operativa.getLabel());
        assertEquals(1000, operativa.getDisplayOrder());
        assertTrue(operativa.isActive());
        assertEquals("LOCAL", operativa.getSource());
        assertNull(operativa.getSyncedAt());

        ArgumentCaptor<List<AlertTypeEntity>> captor = listCaptor();
        verify(repository).saveAll(captor.capture());
        AlertTypeEntity disturbios = captor.getValue().stream()
                .filter(type -> "DISTURBIOS".equals(type.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("Disturbios", disturbios.getLabel());
        assertEquals(3, disturbios.getDisplayOrder());
        assertEquals("MS06", disturbios.getSource());
        assertTrue(disturbios.isActive());
        assertNotNull(disturbios.getSyncedAt());
    }

    @Test
    void synchronize_shouldNotWriteWhenRemoteListIsEmpty() {
        when(client.fetch()).thenReturn(List.of());

        boolean applied = synchronizer.synchronize();

        assertFalse(applied);
        verify(repository, never()).findAllByOrderByDisplayOrderAsc();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_shouldNotWriteWhenRemoteListIsNull() {
        when(client.fetch()).thenReturn(null);

        boolean applied = synchronizer.synchronize();

        assertFalse(applied);
        verify(repository, never()).findAllByOrderByDisplayOrderAsc();
        verify(repository, never()).saveAll(anyList());
    }

    @Test
    void synchronize_shouldNeverTouchLocalTypes() {
        AlertTypeEntity operativa = type("OPERATIVA", "Operativa", 1000, true, "LOCAL");
        when(repository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(operativa));
        when(client.fetch()).thenReturn(List.of(new RemoteAlertType("SISMO", "Sismo")));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        synchronizer.synchronize();

        assertEquals("Operativa", operativa.getLabel());
        assertEquals(1000, operativa.getDisplayOrder());
        assertTrue(operativa.isActive());
        assertEquals("LOCAL", operativa.getSource());
        assertNull(operativa.getSyncedAt());
    }

    private AlertTypeEntity type(
            String code,
            String label,
            int displayOrder,
            boolean active,
            String source) {

        AlertTypeEntity entity = new AlertTypeEntity(code, label, displayOrder, active);
        entity.setSource(source);
        return entity;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<AlertTypeEntity>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
