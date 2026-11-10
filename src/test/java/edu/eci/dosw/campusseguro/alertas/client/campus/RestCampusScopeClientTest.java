package edu.eci.dosw.campusseguro.alertas.client.campus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

/**
 * MS02 autoriza por identidad de usuario (X-User-Id + X-User-Role), no por clave de servicio, así
 * que el cliente propaga la identidad de quien crea o edita la alerta.
 */
class RestCampusScopeClientTest {

    private static final String BUILDING_ID =
            "44444444-4444-4444-8444-444444444444";

    private static final String ZONE_ID =
            "55555555-5555-4555-8555-555555555555";

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private static final String ROLES = "COORDINADOR,ESTUDIANTE";

    private static final String CORRELATION_ID =
            "33333333-3333-4333-8333-333333333333";

    private WireMockServer server;

    private RestCampusScopeClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new RestCampusScopeClient(
                RestClient.builder(),
                server.baseUrl(),
                "service-key",
                "/api/v1/buildings/{id}",
                "/api/v1/safe-zones/{id}",
                2000,
                2000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private void stubFound(String path) {
        server.stubFor(get(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));
    }

    @Test
    void validate_shouldNotCallMs02_whenScopeIsCampus() {
        client.validate(ScopeType.CAMPUS, List.of("NO-EXISTE"), USER_ID, ROLES);

        server.verify(0, getRequestedFor(urlMatching("/api/v1/.*")));
    }

    @Test
    void validate_shouldNotCallMs02_whenIdsAreEmpty() {
        client.validate(ScopeType.BUILDING, List.of(), USER_ID, ROLES);

        server.verify(0, getRequestedFor(urlMatching("/api/v1/.*")));
    }

    @Test
    void validate_shouldPropagateUserIdentity_whenScopeIsBuilding() {
        stubFound("/api/v1/buildings/" + BUILDING_ID);

        MDC.put(CorrelationIds.MDC_KEY, CORRELATION_ID);
        try {
            client.validate(ScopeType.BUILDING, List.of(BUILDING_ID), USER_ID, ROLES);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withHeader("X-Service-Key", equalTo("service-key"))
                .withHeader("X-User-Id", equalTo(USER_ID))
                .withHeader("X-User-Role", equalTo(ROLES))
                .withHeader(CorrelationIds.HEADER, equalTo(CORRELATION_ID)));
    }

    @Test
    void validate_shouldCallSafeZonesEndpoint_whenScopeIsZone() {
        stubFound("/api/v1/safe-zones/" + ZONE_ID);

        client.validate(ScopeType.ZONE, List.of(ZONE_ID), USER_ID, ROLES);

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/safe-zones/" + ZONE_ID)));
    }

    @Test
    void validate_shouldThrowInvalidReference_whenBuildingDoesNotExist() {
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .willReturn(aResponse().withStatus(404)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID), USER_ID, ROLES));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of(BUILDING_ID), ex.getInvalidIds());
    }

    @Test
    void validate_shouldReportOnlyInvalidIds_whenSomeIdsAreValid() {
        stubFound("/api/v1/buildings/" + BUILDING_ID);
        String unknown = UUID.randomUUID().toString();
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + unknown))
                .willReturn(aResponse().withStatus(404)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID, unknown), USER_ID, ROLES));

        assertEquals(List.of(unknown), ex.getInvalidIds());
    }

    @Test
    void validate_shouldThrowExternalServiceUnavailable_whenMs02Fails() {
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID), USER_ID, ROLES));
    }

    @Test
    void validate_shouldThrowExternalServiceUnavailable_whenMs02RejectsTheCall() {
        // 401/403 significa que la identidad no llegó: es una configuración rota, no un id inválido.
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .willReturn(aResponse().withStatus(403)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID), USER_ID, ROLES));
    }
}
