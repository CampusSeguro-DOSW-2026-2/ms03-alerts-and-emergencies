package edu.eci.dosw.campusseguro.alertas.client.campus;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

class RestCampusScopeClientTest {

    private static final String BUILDING_ID = "BLD-003";

    private static final String ZONE_ID = "ZON-001";

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
    void validate_shouldDoNothing_whenScopeIsCampus() {
        client.validate(ScopeType.CAMPUS, List.of("NO-EXISTE"));

        server.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID)));
    }

    @Test
    void validate_shouldDoNothing_whenIdsAreEmpty() {
        client.validate(ScopeType.BUILDING, List.of());

        server.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID)));
    }

    @Test
    void validate_shouldCallBuildingsEndpoint_whenScopeIsBuilding() {
        stubFound("/api/v1/buildings/" + BUILDING_ID);

        MDC.put(CorrelationIds.MDC_KEY, CORRELATION_ID);
        try {
            client.validate(ScopeType.BUILDING, List.of(BUILDING_ID));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withHeader("X-Service-Key", equalTo("service-key"))
                .withHeader(CorrelationIds.HEADER, equalTo(CORRELATION_ID)));
    }

    @Test
    void validate_shouldCallSafeZonesEndpoint_whenScopeIsZone() {
        stubFound("/api/v1/safe-zones/" + ZONE_ID);

        client.validate(ScopeType.ZONE, List.of(ZONE_ID));

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/safe-zones/" + ZONE_ID)));
    }

    @Test
    void validate_shouldThrowInvalidReference_whenBuildingDoesNotExist() {
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .willReturn(aResponse().withStatus(404)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID)));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of(BUILDING_ID), ex.getInvalidIds());
    }

    @Test
    void validate_shouldReportOnlyInvalidIds_whenSomeIdsAreValid() {
        stubFound("/api/v1/buildings/" + BUILDING_ID);
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/NO-EXISTE"))
                .willReturn(aResponse().withStatus(404)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID, "NO-EXISTE")));

        assertEquals(List.of("NO-EXISTE"), ex.getInvalidIds());
    }

    @Test
    void validate_shouldThrowExternalServiceUnavailable_whenMs02Fails() {
        server.stubFor(get(urlPathEqualTo("/api/v1/buildings/" + BUILDING_ID))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validate(ScopeType.BUILDING, List.of(BUILDING_ID)));
    }
}
