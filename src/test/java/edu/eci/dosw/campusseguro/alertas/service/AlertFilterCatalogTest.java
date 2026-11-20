package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterGroupResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterOptionResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertFilterCatalogTest {

    @Mock
    private AlertStateCatalog alertStateCatalog;

    @Mock
    private AlertTypeCatalog alertTypeCatalog;

    private AlertFilterCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new AlertFilterCatalog(alertStateCatalog, alertTypeCatalog);
    }

    @Test
    void groups_shouldReturnStatusThenTypeGroups() {
        stubCatalog();

        List<AlertFilterGroupResponse> groups = catalog.groups();

        assertEquals(
                List.of("status", "type"),
                groups.stream().map(AlertFilterGroupResponse::key).toList());
        verify(alertStateCatalog).options();
        verify(alertTypeCatalog).active();
    }

    @Test
    void statusGroup_shouldBeSingleAndMapLabelsAndFinalState() {
        stubCatalog();

        AlertFilterGroupResponse status = catalog.groups().get(0);

        assertEquals("Estado", status.label());
        assertFalse(status.multiple());
        assertEquals(
                List.of("ACTIVE", "ARCHIVED"),
                status.options().stream().map(AlertFilterOptionResponse::value).toList());
        assertEquals("Activa", status.options().get(0).label());
        assertFalse(status.options().get(0).finalState());
        assertEquals("Archivada", status.options().get(1).label());
        assertTrue(status.options().get(1).finalState());
    }

    @Test
    void typeGroup_shouldBeMultipleAndMapActiveTypesWithoutFinalState() {
        stubCatalog();

        AlertFilterGroupResponse type = catalog.groups().get(1);

        assertEquals("Tipo", type.label());
        assertTrue(type.multiple());
        assertEquals(
                List.of("SISMO", "OPERATIVA"),
                type.options().stream().map(AlertFilterOptionResponse::value).toList());
        assertEquals("Sismo", type.options().get(0).label());
        assertNull(type.options().get(0).finalState());
        assertNull(type.options().get(1).finalState());
    }

    private void stubCatalog() {
        when(alertStateCatalog.options()).thenReturn(List.of(
                new AlertStateCatalog.AlertStateOption("ACTIVE", "Activa", 20, false),
                new AlertStateCatalog.AlertStateOption("ARCHIVED", "Archivada", 30, true)));
        when(alertTypeCatalog.active()).thenReturn(List.of(
                type("SISMO", "Sismo", 1),
                type("OPERATIVA", "Operativa", 1000)));
    }

    private AlertTypeEntity type(String code, String label, int displayOrder) {
        return new AlertTypeEntity(code, label, displayOrder, true);
    }
}
