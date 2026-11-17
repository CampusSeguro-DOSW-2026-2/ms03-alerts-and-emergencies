package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Reglas de estado de una alerta.
 *
 * <p>Qué estados son editables y cuál es terminal lo dice el catálogo, no el código: son columnas de
 * {@code alert_status}.
 */
@Component
public class AlertStateRules {

    private final AlertStateCatalog stateCatalog;

    public AlertStateRules(AlertStateCatalog stateCatalog) {
        this.stateCatalog = stateCatalog;
    }

    /**
     * Indica si la alerta se puede editar en su estado actual.
     *
     * @param alert alerta a comprobar
     * @return true si el catálogo marca el estado como editable
     */
    public boolean isEditable(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        return stateCatalog.isEditable(alert.getStatus());
    }

    /**
     * Comprueba que la alerta se pueda editar.
     *
     * @param alert alerta a comprobar
     * @throws InvalidAlertStateException si el estado actual no es editable
     */
    public void assertEditable(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        if (!isEditable(alert)) {
            throw new InvalidAlertStateException(alert.getStatus());
        }
    }

    /**
     * Comprueba que la alerta no esté en un estado terminal.
     *
     * @param alert alerta a comprobar
     * @throws InvalidAlertStateException si el estado actual es terminal
     */
    public void assertNotArchived(AlertEntity alert) {
        Objects.requireNonNull(alert, "Alert must not be null.");

        if (stateCatalog.isFinal(alert.getStatus())) {
            throw new InvalidAlertStateException(alert.getStatus());
        }
    }
}
