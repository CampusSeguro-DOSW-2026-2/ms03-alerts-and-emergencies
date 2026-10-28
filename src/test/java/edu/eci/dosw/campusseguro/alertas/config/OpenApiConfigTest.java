package edu.eci.dosw.campusseguro.alertas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void api_shouldDocumentIdentityHeadersInsteadOfBearerAuth() {
        OpenAPI api = config.campusSeguroAlertApi();

        assertEquals("2.0.0", api.getInfo().getVersion());
        assertNull(api.getComponents().getSecuritySchemes());
        assertTrue(api.getComponents().getParameters().containsKey("UserIdHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("UserRoleHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("ServiceNameHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("CorrelationIdHeader"));
    }

    @Test
    void identityHeadersCustomizer_shouldAddHeadersToEveryOperation() {
        OpenAPI api = new OpenAPI().path("/api/v1/alertas",
                new PathItem().get(new Operation()));

        config.identityHeadersCustomizer().customise(api);

        assertEquals(4, api.getPaths().get("/api/v1/alertas").getGet().getParameters().size());
    }
}
