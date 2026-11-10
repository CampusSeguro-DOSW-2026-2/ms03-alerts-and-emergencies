package edu.eci.dosw.campusseguro.alertas.client.campus;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.util.List;

/**
 * Valida que los edificios y zonas de una alerta existan en el catálogo del campus (MS02).
 *
 * <p>MS02 no acepta llamadas identificadas solo por clave de servicio: exige la identidad del
 * usuario ({@code X-User-Id} y {@code X-User-Role}). Por eso se propaga la identidad de quien crea
 * o modifica la alerta; todos los roles tienen permiso de lectura del campus.
 */
public interface CampusScopeClient {

    /**
     * @param userId    usuario que origina la operación (UUID), se propaga a MS02
     * @param rolesCsv  roles separados por coma, se propagan a MS02
     * @throws edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException si algún id no existe
     * @throws edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException si MS02 no responde
     */
    void validate(ScopeType type, List<String> ids, String userId, String rolesCsv);
}