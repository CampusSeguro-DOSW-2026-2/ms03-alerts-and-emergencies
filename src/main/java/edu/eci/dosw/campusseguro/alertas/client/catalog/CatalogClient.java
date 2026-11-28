package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.security.CallerIdentity;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CatalogClient {

    /**
     * @param type   tipo de alcance de la alerta
     * @param ids    identificadores de edificios o zonas seguras
     * @param caller identidad que se propaga a MS02
     */
    void validateBuildingsZones(ScopeType type, List<String> ids, CallerIdentity caller);

    void validateRecipients(List<RecipientRequest> recipients);

    Optional<ResolvedUser> findUserById(String userId);

    /**
     * Usuarios activos por id, en una sola consulta cuando el cliente lo permite. Los ids inexistentes o inactivos
     * no aparecen en el mapa.
     */
    default Map<String, ResolvedUser> findUsers(Collection<String> userIds) {
        Map<String, ResolvedUser> found = new LinkedHashMap<>();
        for (String userId : userIds) {
            findUserById(userId).ifPresent(user -> found.put(userId, user));
        }
        return found;
    }

    List<ResolvedUser> getUsersByRole(String role);
}
