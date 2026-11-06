package edu.eci.dosw.campusseguro.alertas.client.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class RestCatalogClientTest {

    private static final String TECHNICAL_USER_ID =
            "88888888-8888-4888-8888-888888888888";

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private static final String CORRELATION_ID =
            "22222222-2222-4222-8222-222222222222";

    private WireMockServer server;
    private RestCatalogClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new RestCatalogClient(
                RestClient.builder(),
                new CatalogStubData(new ObjectMapper()),
                server.baseUrl(),
                TECHNICAL_USER_ID,
                "AUDITOR",
                "service-key",
                2000,
                2000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private String userJson(String id, String estado) {
        return """
                {
                  "id": "%s",
                  "primer_nombre": "Laura",
                  "segundo_nombre": "",
                  "primer_apellido": "Cárdenas",
                  "segundo_apellido": "Rojas",
                  "correo": "laura@example.edu.co",
                  "roles": ["COORDINADOR"],
                  "estado": "%s"
                }
                """.formatted(id, estado);
    }

    private String pageJson(int page, int totalPages) {
        return """
                {
                  "contenido": [
                    %s
                  ],
                  "page": %d,
                  "size": 100,
                  "total_elementos": %d,
                  "total_paginas": %d
                }
                """.formatted(
                userJson(USER_ID, "ACTIVO"),
                page,
                totalPages,
                totalPages);
    }

    @Test
    void validateBuildingsZones_shouldDoNothing_whenScopeIsCampus() {
        client.validateBuildingsZones(ScopeType.CAMPUS, List.of("NO-EXISTE"));

        server.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/users")));
    }

    @Test
    void validateBuildingsZones_shouldValidateLocally_whenScopeIsBuilding() {
        client.validateBuildingsZones(ScopeType.BUILDING, List.of("BLD-001"));

        server.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/users")));
    }

    @Test
    void validateBuildingsZones_shouldThrowInvalidReference_whenBuildingDoesNotExist() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(
                        ScopeType.BUILDING,
                        List.of("BLD-001", "NO-EXISTE")));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of("NO-EXISTE"), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldAcceptExistingUserAndKnownRole() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(USER_ID, "ACTIVO"))));

        MDC.put(CorrelationIds.MDC_KEY, CORRELATION_ID);
        try {
            client.validateRecipients(List.of(
                    new RecipientRequest(RecipientType.USER, USER_ID),
                    new RecipientRequest(RecipientType.ROLE, "ESTUDIANTE")));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }

        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .withHeader("X-User-Id", equalTo(TECHNICAL_USER_ID))
                .withHeader("X-User-Role", equalTo("AUDITOR"))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withHeader("X-Service-Key", equalTo("service-key"))
                .withHeader(CorrelationIds.HEADER, equalTo(CORRELATION_ID)));
    }

    @Test
    void validateRecipients_shouldRejectUnknownUser_whenMs01Returns404() {
        String unknown = UUID.randomUUID().toString();
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + unknown))
                .willReturn(aResponse().withStatus(404)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, unknown))));

        assertEquals(List.of(unknown), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldRejectInactiveUser() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(USER_ID, "INACTIVO"))));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, USER_ID))));

        assertEquals(List.of(USER_ID), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldRejectInvalidRoleWithoutCallingMs01() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.ROLE, "ROL_INEXISTENTE"))));

        assertEquals(List.of("ROL_INEXISTENTE"), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldRejectMalformedUserIdWithoutCallingMs01() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, "usr-001"))));

        assertEquals(List.of("usr-001"), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldThrowExternalServiceUnavailable_whenMs01Fails() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, USER_ID))));
    }

    @Test
    void findUserById_shouldMapMs01Profile_whenUpstreamResponds200() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(userJson(USER_ID, "ACTIVO"))));

        Optional<ResolvedUser> user = client.findUserById(USER_ID);

        assertTrue(user.isPresent());
        assertEquals(USER_ID, user.orElseThrow().id());
        assertEquals("laura@example.edu.co", user.orElseThrow().email());
        assertEquals("Laura Cárdenas Rojas", user.orElseThrow().name());
        assertEquals("ACTIVO", user.orElseThrow().state());
        assertEquals(1, user.orElseThrow().roles().size());
    }

    @Test
    void findUserById_shouldReturnEmpty_whenUpstreamResponds404() {
        String unknown = UUID.randomUUID().toString();
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + unknown))
                .willReturn(aResponse().withStatus(404)));

        assertTrue(client.findUserById(unknown).isEmpty());
    }

    @Test
    void findUserById_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.findUserById(USER_ID));
    }

    @Test
    void getUsersByRole_shouldReadAllPagesAndFilterActiveUsers() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("page", equalTo("0"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pageJson(0, 2))));
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pageJson(1, 2))));

        List<ResolvedUser> users = client.getUsersByRole("COORDINADOR");

        assertEquals(2, users.size());
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("rol", equalTo("COORDINADOR"))
                .withQueryParam("estado", equalTo("ACTIVO"))
                .withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("100")));
        server.verify(getRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("page", equalTo("1")));
    }

    @Test
    void getUsersByRole_shouldThrowInvalidReference_whenMs01RejectsRole() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(400)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.getUsersByRole("NO-EXISTE"));

        assertEquals(List.of("NO-EXISTE"), ex.getInvalidIds());
    }

    @Test
    void getUsersByRole_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.getUsersByRole("ESTUDIANTE"));
    }
}
