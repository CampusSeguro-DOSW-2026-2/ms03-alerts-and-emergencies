package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.security.CallerIdentity;
import java.util.List;
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

    List<ResolvedUser> getUsersByRole(String role);
}
