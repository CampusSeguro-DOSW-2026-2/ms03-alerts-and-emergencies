package edu.eci.dosw.campusseguro.alertas.service;

import org.springframework.stereotype.Component;

/**
 * Máquina de estados de una alerta.
 *
 * <p>Las transiciones válidas son datos: viven en {@code alert_status_transition} y las expone
 * {@link AlertStateCatalog}. Esta clase solo las consulta, así que agregar una transición no requiere
 * tocar código.
 */
@Component
public class AlertStateMachine {

    private final AlertStateCatalog stateCatalog;

    public AlertStateMachine(AlertStateCatalog stateCatalog) {
        this.stateCatalog = stateCatalog;
    }

    /**
     * Indica si el catálogo permite pasar de un estado al otro.
     *
     * @param currentStatus estado actual
     * @param nextStatus estado solicitado
     * @return true si la transición está declarada
     */
    public boolean canTransition(String currentStatus, String nextStatus) {
        return stateCatalog.canTransition(currentStatus, nextStatus);
    }

    /**
     * Comprueba la transición y falla si no está declarada.
     *
     * @param currentStatus estado actual
     * @param nextStatus estado solicitado
     */
    public void assertTransition(String currentStatus, String nextStatus) {
        stateCatalog.assertTransition(currentStatus, nextStatus);
    }
}
