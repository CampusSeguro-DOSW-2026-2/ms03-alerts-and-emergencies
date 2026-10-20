
package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AlertStateRules {

    public boolean isEditable(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        AlertStatus status = alert.getStatus();

        return status == AlertStatus.DRAFT
                || status == AlertStatus.SCHEDULED;
    }

    public void assertEditable(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        if (!isEditable(alert)) {
            throw new IllegalStateException(
                    "An alert can only be edited in DRAFT or SCHEDULED status.");
        }
    }

    public void assertNotArchived(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        if (alert.getStatus() == AlertStatus.ARCHIVED) {
            throw new IllegalStateException(
                    "An archived alert cannot be modified.");
        }
    }
}
