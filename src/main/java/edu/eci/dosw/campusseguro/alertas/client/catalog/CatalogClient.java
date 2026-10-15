package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.util.List;

public interface CatalogClient {

    void validateBuildingsZones(ScopeType type, List<String> ids);

    void validateRecipients(List<RecipientRequest> recipients);

    List<ResolvedUser> getUsersByRole(String role);
}
