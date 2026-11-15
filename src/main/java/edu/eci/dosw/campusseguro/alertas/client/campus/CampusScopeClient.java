package edu.eci.dosw.campusseguro.alertas.client.campus;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.security.CallerIdentity;
import java.util.List;

/**
 * Validación del alcance de una alerta contra MS02.
 *
 * <p>MS02 autoriza por identidad de usuario, así que se propaga la identidad completa de quien crea o
 * edita la alerta: el API Gateway exige las tres cabeceras juntas y rechaza la llamada con 400 si falta
 * alguna.
 */
public interface CampusScopeClient {

    /**
     * Comprueba que los ids del alcance existan en MS02.
     *
     * @param type tipo de alcance, se ignora {@link ScopeType#CAMPUS}
     * @param ids identificadores de edificios o zonas seguras
     * @param caller identidad que se propaga a MS02
     */
    void validate(ScopeType type, List<String> ids, CallerIdentity caller);
}
