package edu.eci.dosw.campusseguro.alertas.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.controller.AlertController;
import edu.eci.dosw.campusseguro.alertas.controller.AuditController;
import edu.eci.dosw.campusseguro.alertas.controller.ConfirmationController;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

class ApiContractTest {

    private static final Path OPENAPI = Path.of("docs", "api", "openapi.yaml");

    private static final List<Class<?>> CONTROLLERS = List.of(
            AlertController.class,
            AuditController.class,
            ConfirmationController.class);

    @Test
    void staticSpec_shouldBeOpenApi31AndDocumentEveryControllerRoute() {
        Map<String, Object> document = loadDocument();

        assertEquals("3.1.0", document.get("openapi"));
        assertEquals(controllerRoutes(), documentedRoutes(document));
    }

    @Test
    void staticSpec_shouldUseMultiTypeContractAndFourStates() {
        Map<String, Object> document = loadDocument();
        Map<String, Object> schemas = schemas(document);

        assertEquals(
                Arrays.stream(AlertStatus.values()).map(Enum::name).toList(),
                enumValues(schemas, "AlertStatus"));
        assertEquals(
                Arrays.stream(AlertType.values()).map(Enum::name).toList(),
                enumValues(schemas, "AlertType"));
        assertTrue(properties(schemas, "AlertCreateRequest").containsKey("types"));
        assertTrue(properties(schemas, "AlertUpdateRequest").containsKey("types"));
        assertTrue(properties(schemas, "AlertResponse").containsKey("types"));
        assertTrue(properties(schemas, "AlertSummaryResponse").containsKey("types"));
        assertFalse(properties(schemas, "AlertResponse").containsKey("type"));
        assertFalse(properties(schemas, "AlertResponse").containsKey("endedAt"));
    }

    @Test
    void staticSpec_shouldDocumentInboxAndNotDocumentLegacyEndEndpoint() {
        Map<String, Object> paths = paths(loadDocument());

        assertTrue(paths.containsKey("/api/v1/alertas/mias"));
        assertFalse(paths.containsKey("/api/v1/alertas/{id}/finalizar"));
        assertFalse(paths.containsKey("/api/v1/alertas/{id}/finalizar/"));
    }

    @Test
    void staticSpec_shouldDocumentEveryInboxField() {
        Map<String, Object> schemas = schemas(loadDocument());

        assertEquals(
                recordFields(InboxAlertResponse.class),
                Set.copyOf(properties(schemas, "InboxAlertResponse").keySet()));
    }

    @Test
    void staticSpec_shouldDocumentSearchAndInboxParameters() {
        Map<String, Object> paths = paths(loadDocument());

        Set<String> searchParameters = parameterNames(paths, "/api/v1/alertas", "get");
        Set<String> inboxParameters = parameterNames(
                paths, "/api/v1/alertas/mias", "get");

        assertTrue(searchParameters.containsAll(
                Set.of("status", "type", "startDate", "endDate", "simulationId", "page", "size")));
        assertTrue(inboxParameters.containsAll(
                Set.of("status", "page", "size")));
    }

    @Test
    void staticSpec_shouldDocumentTheFiveErrorFields() {
        Map<String, Object> schemas = schemas(loadDocument());

        assertEquals(
                Set.of("status", "codigo", "mensaje", "errores", "correlation_id"),
                Set.copyOf(properties(schemas, "ErrorResponse").keySet()));
    }

    private Map<String, Object> loadDocument() {
        try (Reader reader = Files.newBufferedReader(OPENAPI)) {
            return new Yaml().load(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + OPENAPI, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> paths(Map<String, Object> document) {
        return (Map<String, Object>) document.get("paths");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemas(Map<String, Object> document) {
        Map<String, Object> components =
                (Map<String, Object>) document.get("components");
        return (Map<String, Object>) components.get("schemas");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> properties(
            Map<String, Object> schemas, String schemaName) {

        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private List<String> enumValues(
            Map<String, Object> schemas, String schemaName) {

        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        return (List<String>) schema.get("enum");
    }

    @SuppressWarnings("unchecked")
    private Set<String> documentedRoutes(Map<String, Object> document) {
        Set<String> routes = new LinkedHashSet<>();
        paths(document).forEach((path, item) -> {
            Map<String, Object> operations = (Map<String, Object>) item;
            operations.keySet().stream()
                    .filter(key -> List.of("get", "post", "put", "patch", "delete")
                            .contains(key))
                    .forEach(method -> routes.add(
                            method.toUpperCase() + " " + path));
        });
        return routes;
    }

    @SuppressWarnings("unchecked")
    private Set<String> parameterNames(
            Map<String, Object> paths, String path, String method) {

        Map<String, Object> item = (Map<String, Object>) paths.get(path);
        Map<String, Object> operation = (Map<String, Object>) item.get(method);
        List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) operation.get("parameters");

        Set<String> names = new LinkedHashSet<>();
        if (parameters != null) {
            parameters.stream()
                    .map(parameter -> (String) parameter.get("name"))
                    .forEach(names::add);
        }
        names.addAll(headerParameters(operation));
        return names;
    }

    @SuppressWarnings("unchecked")
    private Set<String> headerParameters(Map<String, Object> operation) {
        Set<String> names = new LinkedHashSet<>();
        List<Map<String, Object>> parameters =
                (List<Map<String, Object>>) operation.get("parameters");
        if (parameters == null) {
            return names;
        }
        parameters.stream()
                .map(parameter -> (String) parameter.get("$ref"))
                .filter(ref -> ref != null && ref.contains("Header"))
                .forEach(ref -> names.add(ref.substring(ref.lastIndexOf('/') + 1)));
        return names;
    }

    private Set<String> controllerRoutes() {
        Set<String> routes = new LinkedHashSet<>();
        for (Class<?> controller : CONTROLLERS) {
            RequestMapping baseMapping = controller.getAnnotation(RequestMapping.class);
            String base = baseMapping == null || baseMapping.value().length == 0
                    ? ""
                    : baseMapping.value()[0];

            for (Method method : controller.getDeclaredMethods()) {
                addRoute(routes, base, method.getAnnotation(GetMapping.class));
                addRoute(routes, base, method.getAnnotation(PostMapping.class));
                addRoute(routes, base, method.getAnnotation(PutMapping.class));
                addRoute(routes, base, method.getAnnotation(PatchMapping.class));
                addRoute(routes, base, method.getAnnotation(DeleteMapping.class));
            }
        }
        return routes;
    }

    private void addRoute(Set<String> routes, String base, GetMapping mapping) {
        addRoute(routes, "GET", base, mapping == null ? null : mapping.value());
    }

    private void addRoute(Set<String> routes, String base, PostMapping mapping) {
        addRoute(routes, "POST", base, mapping == null ? null : mapping.value());
    }

    private void addRoute(Set<String> routes, String base, PutMapping mapping) {
        addRoute(routes, "PUT", base, mapping == null ? null : mapping.value());
    }

    private void addRoute(Set<String> routes, String base, PatchMapping mapping) {
        addRoute(routes, "PATCH", base, mapping == null ? null : mapping.value());
    }

    private void addRoute(Set<String> routes, String base, DeleteMapping mapping) {
        addRoute(routes, "DELETE", base, mapping == null ? null : mapping.value());
    }

    private void addRoute(
            Set<String> routes, String method, String base, String[] suffixes) {
        if (suffixes == null) {
            return;
        }
        if (suffixes.length == 0) {
            routes.add(method + " " + base);
            return;
        }
        for (String suffix : suffixes) {
            routes.add(method + " " + base + suffix);
        }
    }

    private Set<String> recordFields(Class<?> type) {
        Set<String> fields = new LinkedHashSet<>();
        for (RecordComponent component : type.getRecordComponents()) {
            fields.add(component.getName());
        }
        return fields;
    }
}
