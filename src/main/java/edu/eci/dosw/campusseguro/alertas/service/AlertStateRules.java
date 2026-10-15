package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import org.springframework.stereotype.Component;

@Component
public class AlertStateRules {

    public boolean isEditable(AlertStatus status) {
        return status == AlertStatus.DRAFT || status == AlertStatus.SCHEDULED;
    }

    public void assertEditable(AlertEntity alert) {
        if (!isEditable(alert.getStatus())) {
            throw new InvalidAlertStateException(alert.getStatus());
        }
    }

    public void assertNotArchived(AlertEntity alert) {
        if (alert.getStatus() == AlertStatus.ARCHIVED) {
            throw new InvalidAlertStateException(alert.getStatus());
        }
    }
}
