package edu.eci.dosw.campusseguro.alertas.client.alerttype;

import java.util.List;

/**
 * Cliente del catálogo de tipos de alerta de MS06.
 *
 * <p>MS06 es la fuente de verdad: devuelve solo los tipos activos y elegibles. MS03 deduce la baja
 * de un tipo por su ausencia en la respuesta.
 */
public interface AlertTypeSyncClient {

    /**
     * Consulta el catálogo vigente en MS06.
     *
     * @return los tipos publicados por MS06; una lista vacía indica que no hay datos utilizables
     */
    List<RemoteAlertType> fetch();
}
