package edu.eci.dosw.campusseguro.alertas.client.campus;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogStubData;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Versión local y de pruebas: valida el alcance contra el catálogo de ejemplo
 * ({@code catalog-stub.json}), sin llamar a MS02.
 */
@Component
@Profile({"local", "test"})
public class StubCampusScopeClient implements CampusScopeClient {

    private final CatalogStubData stubData;

    public StubCampusScopeClient(CatalogStubData stubData) {
        this.stubData = stubData;
    }

    @Override
    public void validate(ScopeType type, List<String> ids) {
        stubData.validateScope(type, ids);
    }
}
