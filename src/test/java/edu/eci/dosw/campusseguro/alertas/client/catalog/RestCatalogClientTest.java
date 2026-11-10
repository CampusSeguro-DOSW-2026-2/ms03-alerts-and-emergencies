package edu.eci.dosw.campusseguro.alertas.client.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
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

/**
 * El cliente usa las rutas internas de MS01 ({@code /users/batch} y {@code /users/by-role}), que se
 * autorizan por {@code X-Service-Name}: no se envía identidad de usuario.
 */
class RestCatalogClientTest {

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private static final String CORRELATION_ID =
            "22222222-2222-4222-8222-222222222222";

    private static final String BATCH_PATH = "/api/v1/users/batch";

    private static final String BY_ROLE_PATH = "/api/v1/users/by-role";

    private WireMockServer server;

    private RestCatalogClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new RestCatalogClient(
                RestClient.builder(),
                (type, ids, userId, roles) -> {
                    // La validación del alcance se prueba en RestCampusScopeClientTest.
                },
                server.baseUrl(),
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

    private String batchJson(String usuarios, String noEncontrados) {
        return """
                {
                  "usuarios": [%s],
                  "no_encontrados": [%s]
                }
                """.formatted(usuarios, noEncontrados);
    }

    private String pageJson(int page, int totalPages, String estado) {
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
                userJson(USER_ID, estado),
                page,
                totalPages,
                totalPages);
    }

    private void stubBatch(String body) {
        server.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void validateRecipients_shouldAcceptExistingUserAndKnownRole() {
        stubBatch(batchJson(userJson(USER_ID, "ACTIVO"), ""));

        MDC.put(CorrelationIds.MDC_KEY, CORRELATION_ID);
        try {
            client.validateRecipients(List.of(
                    new RecipientRequest(RecipientType.USER, USER_ID),
                    new RecipientRequest(RecipientType.ROLE, "ESTUDIANTE")));
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }

        server.verify(postRequestedFor(urlPathEqualTo(BATCH_PATH))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withHeader("X-Service-Key", equalTo("service-key"))
                .withHeader(CorrelationIds.HEADER, equalTo(CORRELATION_ID))
                .withoutHeader("X-User-Id")
                .withoutHeader("X-User-Role"));
    }

    @Test
    void validateRecipients_shouldRejectUnknownUser_whenBatchDoesNotReturnIt() {
        String unknown = UUID.randomUUID().toString();
        stubBatch(batchJson("", "\"" + unknown + "\""));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, unknown))));

        assertEquals(List.of(unknown), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldRejectInactiveUser() {
        stubBatch(batchJson(userJson(USER_ID, "INACTIVO"), ""));

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
        server.verify(0, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void validateRecipients_shouldRejectMalformedUserIdWithoutCallingMs01() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, "usr-001"))));

        assertEquals(List.of("usr-001"), ex.getInvalidIds());
        server.verify(0, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void validateRecipients_shouldThrowExternalServiceUnavailable_whenMs01Fails() {
        server.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validateRecipients(List.of(
                        new RecipientRequest(RecipientType.USER, USER_ID))));
    }

    @Test
    void findUserById_shouldMapMs01Profile_whenBatchReturnsIt() {
        stubBatch(batchJson(userJson(USER_ID, "ACTIVO"), ""));

        Optional<ResolvedUser> user = client.findUserById(USER_ID);

        assertTrue(user.isPresent());
        assertEquals(USER_ID, user.orElseThrow().id());
        assertEquals("laura@example.edu.co", user.orElseThrow().email());
        assertEquals("Laura Cárdenas Rojas", user.orElseThrow().name());
        assertEquals("ACTIVO", user.orElseThrow().state());
        assertEquals(1, user.orElseThrow().roles().size());
    }

    @Test
    void findUserById_shouldReturnEmpty_whenBatchDoesNotReturnIt() {
        String unknown = UUID.randomUUID().toString();
        stubBatch(batchJson("", "\"" + unknown + "\""));

        assertTrue(client.findUserById(unknown).isEmpty());
    }

    @Test
    void findUserById_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(post(urlPathEqualTo(BATCH_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.findUserById(USER_ID));
    }

    @Test
    void getUsersByRole_shouldReadAllPagesAndFilterActiveUsers() {
        server.stubFor(get(urlPathEqualTo(BY_ROLE_PATH))
                .withQueryParam("page", equalTo("0"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pageJson(0, 2, "ACTIVO"))));
        server.stubFor(get(urlPathEqualTo(BY_ROLE_PATH))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(pageJson(1, 2, "INACTIVO"))));

        List<ResolvedUser> users = client.getUsersByRole("COORDINADOR");

        assertEquals(1, users.size());
        server.verify(getRequestedFor(urlPathEqualTo(BY_ROLE_PATH))
                .withQueryParam("rol", equalTo("COORDINADOR"))
                .withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("100")));
        server.verify(getRequestedFor(urlPathEqualTo(BY_ROLE_PATH))
                .withQueryParam("page", equalTo("1")));
    }

    @Test
    void getUsersByRole_shouldThrowInvalidReference_whenMs01RejectsRole() {
        server.stubFor(get(urlPathEqualTo(BY_ROLE_PATH))
                .willReturn(aResponse().withStatus(400)));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.getUsersByRole("NO-EXISTE"));

        assertEquals(List.of("NO-EXISTE"), ex.getInvalidIds());
    }

    @Test
    void getUsersByRole_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(get(urlPathEqualTo(BY_ROLE_PATH))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.getUsersByRole("ESTUDIANTE"));
    }

    @Test
    void validateRecipients_shouldNotCallMs01_whenListIsEmpty() {
        client.validateRecipients(List.of());

        server.verify(0, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }

    @Test
    void validateRecipients_shouldChunkMoreThanOneHundredUsers() {
        List<RecipientRequest> recipients = java.util.stream.IntStream.range(0, 101)
                .mapToObj(index -> new RecipientRequest(
                        RecipientType.USER, UUID.randomUUID().toString()))
                .toList();
        stubBatch(batchJson("", ""));

        assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(recipients));

        server.verify(2, postRequestedFor(urlPathEqualTo(BATCH_PATH)));
    }
}
