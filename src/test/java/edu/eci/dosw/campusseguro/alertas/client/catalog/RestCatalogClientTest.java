package edu.eci.dosw.campusseguro.alertas.client.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestCatalogClientTest {

    private WireMockServer server;
    private RestCatalogClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new RestCatalogClient(
                RestClient.builder(),
                server.baseUrl(),
                server.baseUrl(),
                1000,
                300,
                "service-token",
                "/catalog/validate-scope",
                "/catalog/validate-recipients",
                "/users/{id}",
                "/users?role={role}");
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void validateBuildingsZones_shouldDoNothing_whenScopeIsCampus() {
        client.validateBuildingsZones(ScopeType.CAMPUS, List.of("B1"));

        server.verify(0, postRequestedFor(urlEqualTo("/catalog/validate-scope")));
    }

    @Test
    void validateBuildingsZones_shouldSendIdsAndServiceToken_whenScopeIsBuilding() {
        server.stubFor(post("/catalog/validate-scope")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[]}")));

        client.validateBuildingsZones(ScopeType.BUILDING, List.of("B1", "B2"));

        server.verify(postRequestedFor(urlEqualTo("/catalog/validate-scope"))
                .withHeader("Authorization", equalTo("Bearer service-token"))
                .withRequestBody(equalToJson("{\"tipo\":\"BUILDING\",\"ids\":[\"B1\",\"B2\"]}")));
    }

    @Test
    void validateBuildingsZones_shouldThrowInvalidReference_whenResponseContainsInvalidIds() {
        server.stubFor(post("/catalog/validate-scope")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[\"B9\",\"B8\"]}")));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of("B9", "B8")));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of("B9", "B8"), ex.getInvalidIds());
    }

    @Test
    void validateBuildingsZones_shouldThrowInvalidReference_whenUpstreamReturns400WithDetails() {
        server.stubFor(post("/catalog/validate-scope")
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[\"Z9\"]}")));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.ZONE, List.of("Z9")));

        assertEquals(List.of("Z9"), ex.getInvalidIds());
    }

    @Test
    void validateBuildingsZones_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(post("/catalog/validate-scope")
                .willReturn(aResponse().withStatus(500)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of("B1")));
    }

    @Test
    void validateBuildingsZones_shouldThrowExternalServiceUnavailable_whenRequestTimesOut() {
        server.stubFor(post("/catalog/validate-scope")
                .willReturn(aResponse().withStatus(200).withFixedDelay(1000)));

        assertThrows(
                ExternalServiceUnavailableException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of("B1")));
    }

    @Test
    void validateRecipients_shouldSendRecipientsAndThrowInvalidReference_whenSomeAreMissing() {
        server.stubFor(post("/catalog/validate-recipients")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"invalidIds\":[\"STUDENT\"]}")));

        List<RecipientRequest> recipients = List.of(
                new RecipientRequest(RecipientType.USER, "usr-001"),
                new RecipientRequest(RecipientType.ROLE, "STUDENT"));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(recipients));

        assertEquals("destinatarios", ex.getField());
        assertEquals(List.of("STUDENT"), ex.getInvalidIds());
        server.verify(postRequestedFor(urlEqualTo("/catalog/validate-recipients"))
                .withRequestBody(equalToJson(
                        "{\"destinatarios\":[{\"tipo\":\"USER\",\"id\":\"usr-001\"},{\"tipo\":\"ROLE\",\"id\":\"STUDENT\"}]}")));
    }

    @Test
    void findUserById_shouldReturnUser_whenUpstreamResponds200() {
        server.stubFor(get("/users/usr-001")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"usr-001\",\"email\":\"laura@example.edu.co\",\"name\":\"Laura\"}")));

        Optional<ResolvedUser> user = client.findUserById("usr-001");

        assertTrue(user.isPresent());
        assertEquals("laura@example.edu.co", user.orElseThrow().email());
    }

    @Test
    void findUserById_shouldReturnEmpty_whenUpstreamResponds404() {
        server.stubFor(get("/users/usr-404").willReturn(aResponse().withStatus(404)));

        assertTrue(client.findUserById("usr-404").isEmpty());
    }

    @Test
    void findUserById_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(get("/users/usr-001").willReturn(aResponse().withStatus(503)));

        assertThrows(ExternalServiceUnavailableException.class, () -> client.findUserById("usr-001"));
    }

    @Test
    void getUsersByRole_shouldReturnUsers_whenUpstreamResponds200() {
        server.stubFor(get("/users?role=STUDENT")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"usr-001\",\"email\":\"laura@example.edu.co\",\"name\":\"Laura\"}]")));

        List<ResolvedUser> users = client.getUsersByRole("STUDENT");

        assertEquals(1, users.size());
        assertEquals("usr-001", users.get(0).id());
        server.verify(getRequestedFor(urlEqualTo("/users?role=STUDENT")));
    }

    @Test
    void getUsersByRole_shouldThrowExternalServiceUnavailable_whenUpstreamFails() {
        server.stubFor(get("/users?role=STUDENT").willReturn(aResponse().withStatus(500)));

        assertThrows(ExternalServiceUnavailableException.class, () -> client.getUsersByRole("STUDENT"));
    }
}
