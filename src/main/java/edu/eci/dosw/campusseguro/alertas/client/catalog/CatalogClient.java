package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.util.List;
import java.util.Optional;

public interface CatalogClient {

    /**
     * @param userId   usuario que origina la operación (UUID), se propaga a MS02
     * @param rolesCsv roles separados por coma, se propagan a MS02
     */
    void validateBuildingsZones(ScopeType type, List<String> ids, String userId, String rolesCsv);

    void validateRecipients(List<RecipientRequest> recipients);

    Optional<ResolvedUser> findUserById(String userId);

    List<ResolvedUser> getUsersByRole(String role);
}