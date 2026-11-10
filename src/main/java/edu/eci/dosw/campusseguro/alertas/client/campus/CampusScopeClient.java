package edu.eci.dosw.campusseguro.alertas.client.campus;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.util.List;

/**
 * Valida que los edificios y zonas de una alerta existan en el catálogo del campus (MS02).
 */
public interface CampusScopeClient {

    /**
     * @throws edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException si algún id no existe
     * @throws edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException si MS02 no responde
     */
    void validate(ScopeType type, List<String> ids);
}
