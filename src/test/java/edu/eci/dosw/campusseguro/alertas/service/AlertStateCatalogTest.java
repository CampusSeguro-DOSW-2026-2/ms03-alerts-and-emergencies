package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * El snapshot interno es un mapa sin orden garantizado. Estas pruebas comprueban que
 * {@link AlertStateCatalog#options()} impone el orden de presentación por sí mismo.
 */
@ExtendWith(MockitoExtension.class)
class AlertStateCatalogTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void options_shouldSortByDisplayOrderThenCode() throws Exception {
        stubStatuses(
                status("ARCHIVED", "Archivada", 30, false, true),
                status("ACTIVE", "Activa", 20, false, false),
                status("DRAFT", "Borrador", 10, true, false));

        AlertStateCatalog catalog = new AlertStateCatalog(jdbcTemplate);

        List<AlertStateCatalog.AlertStateOption> options = catalog.options();

        assertEquals(
                List.of("DRAFT", "ACTIVE", "ARCHIVED"),
                options.stream().map(AlertStateCatalog.AlertStateOption::code).toList());
        assertEquals("Borrador", options.get(0).label());
        assertEquals(10, options.get(0).displayOrder());
        assertFalse(options.get(0).finalState());
        assertEquals("Archivada", options.get(2).label());
        assertEquals(30, options.get(2).displayOrder());
        assertTrue(options.get(2).finalState());
    }

    @Test
    void options_shouldTieBreakByCodeWhenDisplayOrderMatches() throws Exception {
        stubStatuses(
                status("SANDBOX", "Sandbox", 10, false, false),
                status("ACTIVE", "Activa", 10, false, false),
                status("DRAFT", "Borrador", 10, true, false));

        AlertStateCatalog catalog = new AlertStateCatalog(jdbcTemplate);

        List<AlertStateCatalog.AlertStateOption> options = catalog.options();

        assertEquals(
                List.of("ACTIVE", "DRAFT", "SANDBOX"),
                options.stream().map(AlertStateCatalog.AlertStateOption::code).toList());
    }

    @SafeVarargs
    private void stubStatuses(Map<String, Object>... rows) throws Exception {
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            for (Map<String, Object> row : rows) {
                handler.processRow(resultSet(row));
            }
            return null;
        }).when(jdbcTemplate).query(
                contains("final_state"),
                any(RowCallbackHandler.class));
    }

    private Map<String, Object> status(
            String code,
            String label,
            int displayOrder,
            boolean editable,
            boolean finalState) {

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        row.put("display_order", displayOrder);
        row.put("editable", editable);
        row.put("final_state", finalState);
        return row;
    }

    private ResultSet resultSet(Map<String, Object> row) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("code")).thenReturn((String) row.get("code"));
        when(resultSet.getString("label")).thenReturn((String) row.get("label"));
        when(resultSet.getInt("display_order")).thenReturn((Integer) row.get("display_order"));
        when(resultSet.getBoolean("editable")).thenReturn((Boolean) row.get("editable"));
        when(resultSet.getBoolean("final_state")).thenReturn((Boolean) row.get("final_state"));
        return resultSet;
    }
}
