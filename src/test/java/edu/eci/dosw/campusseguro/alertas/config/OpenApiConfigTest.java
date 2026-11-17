package edu.eci.dosw.campusseguro.alertas.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        assertEquals("3.0.0", api.getInfo().getVersion());
        assertNull(api.getComponents().getSecuritySchemes());
        assertTrue(api.getComponents().getParameters().containsKey("UserIdHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("UserRoleHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("UserPermissionHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("ServiceNameHeader"));
        assertTrue(api.getComponents().getParameters().containsKey("CorrelationIdHeader"));
    }

    @Test
    void permissionHeader_shouldBeRequiredAndTheAuthorizationSource() {
        OpenAPI api = config.campusSeguroAlertApi();

        var header = api.getComponents().getParameters().get("UserPermissionHeader");

        assertEquals("X-User-Permission", header.getName());
        assertTrue(header.getRequired());
        assertTrue(header.getDescription().contains("autorización"));
    }

    @Test
    void roleHeader_shouldBeInformativeOnly() {
        OpenAPI api = config.campusSeguroAlertApi();

        var header = api.getComponents().getParameters().get("UserRoleHeader");

        assertTrue(header.getDescription().contains("no conceden permisos"));
    }

    @Test
    void serviceNameHeader_shouldDescribeAuditMetadataWithoutMs06Rules() {
        OpenAPI api = config.campusSeguroAlertApi();

        var header = api.getComponents().getParameters().get("ServiceNameHeader");

        assertTrue(header.getDescription().contains("auditoría"));
        assertTrue(header.getDescription().contains("No altera"));
        assertFalse(header.getDescription().contains("ms06"));
    }

    @Test
    void identityHeadersCustomizer_shouldAddHeadersToEveryOperation() {
        OpenAPI api = new OpenAPI().path("/api/v1/alertas",
                new PathItem().get(new Operation()));

        config.identityHeadersCustomizer().customise(api);

        assertEquals(5, api.getPaths().get("/api/v1/alertas").getGet().getParameters().size());
    }
}
