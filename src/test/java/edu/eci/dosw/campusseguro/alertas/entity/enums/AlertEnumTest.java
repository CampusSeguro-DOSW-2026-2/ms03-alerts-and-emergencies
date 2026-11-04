package edu.eci.dosw.campusseguro.alertas.entity.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertEnumTest {

    @Test
    void alertType_shouldExposeOnlyTheCurrentCatalog() {
        assertEquals(
                List.of(
                        AlertType.EARTHQUAKE,
                        AlertType.FIRE,
                        AlertType.OTHER,
                        AlertType.OPERATIVA),
                Arrays.asList(AlertType.values()));
    }

    @Test
    void alertStatus_shouldExposeOnlyTheFourLifecycleStates() {
        assertEquals(
                List.of(
                        AlertStatus.DRAFT,
                        AlertStatus.SCHEDULED,
                        AlertStatus.ACTIVE,
                        AlertStatus.ARCHIVED),
                Arrays.asList(AlertStatus.values()));
        assertFalse(Arrays.stream(AlertStatus.values())
                .anyMatch(status -> status.name().equals("ENDED")));
    }

    @Test
    void auditStatus_shouldKeepLegacyEndedForReadOnlyCompatibility() {
        assertTrue(Arrays.stream(AuditStatus.values())
                .anyMatch(status -> status.name().equals("ENDED")));
        assertTrue(Arrays.stream(AuditAction.values())
                .anyMatch(action -> action.name().equals("END")));
    }
}
